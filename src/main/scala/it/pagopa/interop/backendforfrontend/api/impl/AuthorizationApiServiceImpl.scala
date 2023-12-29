package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.backendforfrontend.common.HeaderUtils._
import akka.http.scaladsl.server.Directives.{complete, onComplete, redirect}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import cats.implicits._
import com.nimbusds.jwt.JWTClaimsSet
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.AuthorizationApiService
import it.pagopa.interop.backendforfrontend.api.impl.Utils.{
  buildJwtCustomClaims,
  buildSupportClaims,
  parseResponse,
  validate
}
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.BFFErrors.{MissingSelfcareId, SelfcareNotFound, UnknownTenantOrigin}
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model.{IdentityToken, Problem, SessionToken}
import it.pagopa.interop.backendforfrontend.service.{SelfcareV2ClientService, TenantProcessService}
import it.pagopa.interop.commons.jwt.service.{InteropTokenGenerator, JWTReader, SessionTokenGenerator}
import it.pagopa.interop.commons.jwt.{getUserRoles, organizationClaim}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.ratelimiter.RateLimiter
import it.pagopa.interop.commons.ratelimiter.model.{Headers, RateLimitStatus}
import it.pagopa.interop.commons.signer.model.SignatureAlgorithm
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.MissingClaim
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.interop.backendforfrontend.service.types.SelfcareV2ClientServiceTypes._
import it.pagopa.interop.tenantprocess.client.model.TenantUnitType

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import java.util.UUID
import scala.util.{Failure, Success, Try}

final case class AuthorizationApiServiceImpl(
  jwtReader: JWTReader,
  sessionTokenGenerator: SessionTokenGenerator,
  interopTokenGenerator: InteropTokenGenerator,
  tenantProcessService: TenantProcessService,
  selfcareV2ClientService: SelfcareV2ClientService,
  offsetDateTimeSupplier: OffsetDateTimeSupplier,
  allowList: List[String],
  rateLimiter: RateLimiter
)(implicit ec: ExecutionContext)
    extends AuthorizationApiService {

  private val NAME: String        = "name"
  private val FAMILY_NAME: String = "family_name"
  private val EMAIL: String       = "email"

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private val admittedSessionClaims: Set[String] = Set(UID, ORGANIZATION, NAME, FAMILY_NAME, EMAIL)

  override def getSessionToken(identityToken: IdentityToken)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerSessionToken: ToEntityMarshaller[SessionToken]
  ): Route = {
    val result: Future[(SessionToken, RateLimitStatus)] = for {
      (sessionClaims, roles, selfcareId) <- readJwt(identityToken).toFuture
      internalContexts                   <- generateInternalTokenContexts(interopTokenGenerator, sessionClaims)
      tenantId <- getTenantOr(selfcareId)(upsertTenantBySelfcareId(selfcareId)(internalContexts))(internalContexts)
      tenant   <- tenantProcessService.getTenant(tenantId)(internalContexts)
      _        <- assertTenantAllowed(selfcareId, tenant.externalId.origin)
      rateLimitStatus <- rateLimiter.rateLimiting(tenantId)
      customClaims = buildJwtCustomClaims(
        roles,
        tenantId,
        selfcareId,
        tenant.externalId.origin,
        tenant.externalId.value
      )
      token <- sessionTokenGenerator.generate(
        SignatureAlgorithm.RSAPkcs1Sha256,
        sessionClaims ++ customClaims.widen[AnyRef],
        ApplicationConfiguration.generatedJwtAudience,
        ApplicationConfiguration.generatedJwtIssuer,
        ApplicationConfiguration.generatedJwtDuration
      )
    } yield (SessionToken(token), rateLimitStatus)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error creating a session token", headers) orElse { case Success((token, rateLimitStatus)) =>
        complete(StatusCodes.OK, headers ++ Headers.headersFromStatus(rateLimitStatus), token)
      }
    }
  }

  private final val allowedOrigins: Set[String] = Set("IPA", "ANAC", "IVASS")

  private def assertTenantAllowed(selfcareId: String, origin: String): Future[Unit] =
    if (allowedOrigins.contains(origin) || allowList.contains(selfcareId)) Future.successful(())
    else Future.failed(UnknownTenantOrigin(selfcareId))

  private def getTenantOr(
    selfcareId: String
  )(alternative: => Future[UUID])(implicit contexts: Seq[(String, String)]): Future[UUID] = {
    for {
      selfcareUuid <- selfcareId.toFutureUUID
      id           <- tenantProcessService
        .getBySelfcareId(selfcareUuid)
        .map(_.id)
        .recoverWith { case _: SelfcareNotFound => alternative }
    } yield id
  }

  private def upsertTenantBySelfcareId(selfcareId: String)(implicit contexts: Seq[(String, String)]): Future[UUID] =
    for {
      selfcareUuid      <- selfcareId.toFutureUUID
      institution       <- selfcareV2ClientService.getInstitution(selfcareUuid)
      institutionApi    <- institution.toApi.toFuture
      _                 <- assertTenantAllowed(selfcareId, institutionApi.origin)
      subUnitType       <- institutionApi.subUnitType.traverse(TenantUnitType.fromValue(_).toFuture)
      onboardingData    <- selfcareV2ClientService.getOnboardingsInstitution(institutionApi.id, None)
      onboardingDataApi <- onboardingData.toApi.toFuture
      externalId =
        if (institutionApi.origin == "IPA") institutionApi.subunitCode.getOrElse(institutionApi.originId)
        else institutionApi.taxCode
      resourceId <- tenantProcessService
        .selfcareUpsertTenant(
          institutionApi.origin,
          externalId,
          institutionApi.description,
          None,
          onboardingDataApi.onboardedAt,
          subUnitType
        )(institutionApi.id.toString)
    } yield resourceId.id

  def readJwt(identityToken: IdentityToken): Try[(Map[String, AnyRef], String, String)] = for {
    claims        <- jwtReader.getClaims(identityToken.identity_token)
    sessionClaims <- extractSessionClaims(claims)
    selfcareId    <- getSelfcareId(claims)
  } yield (sessionClaims, getUserRoles(claims).mkString(","), selfcareId)

  private def extractSessionClaims(claims: JWTClaimsSet): Try[Map[String, AnyRef]] = Try {
    claims.getClaims.asScala.view.filterKeys(admittedSessionClaims.contains).toMap
  }

  private def getSelfcareId(claims: JWTClaimsSet): Try[String] = for {
    nullableOrgClaimsMap <- Try(claims.getJSONObjectClaim(organizationClaim))
      .leftMap(_ => MissingClaim(s"$organizationClaim in selfcare token"))
    orgClaims            <- Option(nullableOrgClaimsMap).toTry(MissingClaim(s"$organizationClaim in selfcare token"))
    orgClaimsMap = orgClaims.asScala.toMap
    organizationId <- orgClaimsMap.get("id").toTry(MissingClaim("id in organization in selfcare token"))
  } yield organizationId.toString

  override def samlLoginCallback(sAMLResponse: String, relayState: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    logger.info(s"Calling Support SAML")

    val result: Future[(String, String)] = for {
      responseDecoded <- sAMLResponse.decodeBase64.toFuture
      responseXml     <- parseResponse(responseDecoded).toFuture
      _               <- validate(responseXml)(offsetDateTimeSupplier).toFuture
      tenant          <- tenantProcessService.getTenant(ApplicationConfiguration.pagoPaTenantId)
      selfcareId      <- tenant.selfcareId.toFuture(MissingSelfcareId(tenant.id))
      sessionToken    <- sessionTokenGenerator.generate(
        signatureAlgorithm = SignatureAlgorithm.RSAPkcs1Sha256,
        claimsSet = buildSupportClaims(selfcareId, tenant),
        audience = ApplicationConfiguration.generatedJwtAudience,
        tokenIssuer = ApplicationConfiguration.generatedJwtIssuer,
        validityDurationInSeconds = ApplicationConfiguration.supportLandingJwtDuration
      )
    } yield (sAMLResponse, sessionToken)

    onComplete(result) {
      case Failure(ex)                     =>
        logger.error(s"Error calling support SAML - ${ex.getMessage}")
        val redirectUrl = s"${ApplicationConfiguration.saml2CallbackErrorUrl}"
        redirect(redirectUrl, StatusCodes.Found)
      case Success((base64, sessionToken)) =>
        val redirectUrl = s"${ApplicationConfiguration.saml2CallbackUrl}#saml2=$base64&jwt=$sessionToken"
        redirect(redirectUrl, StatusCodes.Found)
    }
  }

}
