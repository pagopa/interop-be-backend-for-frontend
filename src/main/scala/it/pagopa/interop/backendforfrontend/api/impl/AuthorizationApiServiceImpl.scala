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
import it.pagopa.interop.backendforfrontend.api.impl.Utils.{buildClaims, parseResponse, validate}
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.BFFErrors.{SelfcareNotFound, UnknownTenantOrigin}
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model.{IdentityToken, Problem, SessionToken}
import it.pagopa.interop.backendforfrontend.service.{PartyProcessService, TenantProcessService}
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
import it.pagopa.interop.tenantprocess.client.model.Tenant

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

final case class AuthorizationApiServiceImpl(
  jwtReader: JWTReader,
  sessionTokenGenerator: SessionTokenGenerator,
  interopTokenGenerator: InteropTokenGenerator,
  tenantProcessService: TenantProcessService,
  partyProcess: PartyProcessService,
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
      tenant <- getTenantOr(selfcareId)(upsertTenantBySelfcareId(selfcareId)(internalContexts))(internalContexts)
      rateLimitStatus <- rateLimiter.rateLimiting(tenant.id)
      customClaims: Map[String, AnyRef] = Map(
        USER_ROLES                     -> roles,
        ORGANIZATION_ID_CLAIM          -> tenant.id.toString,
        SELFCARE_ID_CLAIM              -> selfcareId,
        ORGANIZATION_EXTERNAL_ID_CLAIM -> Map(
          ORGANIZATION_EXTERNAL_ID_ORIGIN_CLAIM -> tenant.externalId.origin,
          ORGANIZATION_EXTERNAL_ID_VALUE_CLAIM  -> tenant.externalId.value
        ).asJava
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

  private final val allowedOrigins: Set[String] = Set("IPA", "ANAC")

  private def assertTenantAllowed(selfcareId: String, origin: String): Future[Unit] =
    if (allowedOrigins.contains(origin) || allowList.contains(selfcareId)) Future.successful(())
    else Future.failed(UnknownTenantOrigin(selfcareId))

  private def getTenantOr(
    selfcareId: String
  )(alternative: => Future[Tenant])(implicit contexts: Seq[(String, String)]): Future[Tenant] = {
    for {
      selfcareUuid <- selfcareId.toFutureUUID
      tenant       <- tenantProcessService
        .getBySelfcareId(selfcareUuid)
        .recoverWith { case _: SelfcareNotFound => alternative }
      _            <- assertTenantAllowed(selfcareId, tenant.externalId.origin)
    } yield tenant
  }

  private def upsertTenantBySelfcareId(selfcareId: String)(implicit contexts: Seq[(String, String)]): Future[Tenant] =
    for {
      partyInstitution <- partyProcess.getInstitution(selfcareId)
      _                <- assertTenantAllowed(selfcareId, partyInstitution.origin)
      tenant           <- tenantProcessService
        .selfcareUpsertTenant(partyInstitution.origin, partyInstitution.originId, partyInstitution.description)(
          partyInstitution.id.toString
        )
    } yield tenant

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
      sessionToken    <- sessionTokenGenerator.generate(
        signatureAlgorithm = SignatureAlgorithm.RSAPkcs1Sha256,
        claimsSet = buildClaims(ApplicationConfiguration.pagoPaTenantId),
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
