package it.pagopa.interop.backendforfrontend.api.impl

import cats.implicits._
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import com.nimbusds.jwt.JWTClaimsSet
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.AuthorizationApiService
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model.{IdentityToken, SessionToken}
import it.pagopa.interop.commons.jwt.service.{JWTReader, SessionTokenGenerator}
import it.pagopa.interop.commons.jwt.{getUserRoles, organizationClaim}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.ratelimiter.RateLimiter
import it.pagopa.interop.commons.ratelimiter.model.{Headers, RateLimitStatus}
import it.pagopa.interop.commons.signer.model.SignatureAlgorithm
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.MissingClaim
import it.pagopa.interop.commons.utils.{ORGANIZATION, ORGANIZATION_ID_CLAIM, UID, USER_ROLES}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.util.{Success, Try}
import it.pagopa.interop.backendforfrontend.service.{TenantManagementService, TenantProcessService}
import it.pagopa.interop.backendforfrontend.service.PartyProcessService

final case class AuthorizationApiServiceImpl(
  jwtReader: JWTReader,
  sessionTokenGenerator: SessionTokenGenerator,
  tenantManagement: TenantManagementService,
  tenantProcess: TenantProcessService,
  partyProcess: PartyProcessService,
  rateLimiter: RateLimiter
)(implicit ec: ExecutionContext)
    extends AuthorizationApiService {

  private val NAME: String        = "name"
  private val FAMILY_NAME: String = "family_name"
  private val EMAIL: String       = "email"

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private val admittedSessionClaims: Set[String] = Set(UID, ORGANIZATION, NAME, FAMILY_NAME, EMAIL)

  // TODO move me in commons
  private val SELFCARE_ID_CLAIM = "selfcareId"

  override def getSessionToken(identityToken: IdentityToken)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerSessionToken: ToEntityMarshaller[SessionToken]
  ): Route = {
    val result: Future[(SessionToken, RateLimitStatus)] = for {
      (sessionClaims, roles, selfcareId) <- readJwt(identityToken).toFuture
      tenantId                           <- getTenantIdOr(selfcareId)(upsertTenantBySelfcareId(selfcareId))
      rateLimitStatus                    <- rateLimiter.rateLimiting(tenantId)
      customClaims: Map[String, String] = Map(
        USER_ROLES            -> roles.toString(),
        ORGANIZATION_ID_CLAIM -> tenantId.toString(),
        SELFCARE_ID_CLAIM     -> selfcareId.toString()
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
      handleError(s"Error creating a session token") orElse { case Success((token, rateLimitStatus)) =>
        complete(StatusCodes.OK, Headers.headersFromStatus(rateLimitStatus), token)
      }
    }
  }

  private def getTenantIdOr(
    selfcareId: UUID
  )(alternative: => Future[UUID])(implicit contexts: Seq[(String, String)]): Future[UUID] =
    tenantManagement.getBySelfcareId(selfcareId).map(_.id).recoverWith {
      case ex if TenantManagementService.is404(ex) => alternative
    }

  private def upsertTenantBySelfcareId(selfcareId: UUID)(implicit contexts: Seq[(String, String)]): Future[UUID] = for {
    partyInstitution <- partyProcess.getInstitution(selfcareId)
    tenantId         <- tenantProcess
      .selfcareUpsertTenant(partyInstitution.origin, partyInstitution.originId)(partyInstitution.id)
      .map(_.id)
  } yield tenantId

  def readJwt(identityToken: IdentityToken): Try[(Map[String, AnyRef], String, UUID)] = for {
    claims        <- jwtReader.getClaims(identityToken.identity_token)
    sessionClaims <- extractSessionClaims(claims)
    selfcareId    <- getOrganizationId(claims)
  } yield (sessionClaims, getUserRoles(claims).mkString(","), selfcareId)

  private def extractSessionClaims(claims: JWTClaimsSet): Try[Map[String, AnyRef]] = Try {
    claims.getClaims.asScala.view.filterKeys(admittedSessionClaims.contains).toMap
  }

  private def getOrganizationId(claims: JWTClaimsSet): Try[UUID] = for {
    nullableOrgClaimsMap <- Try(claims.getJSONObjectClaim(organizationClaim))
      .leftMap(_ => MissingClaim(s"$organizationClaim in selfcare token"))
    orgClaims            <- Option(nullableOrgClaimsMap).toTry(MissingClaim(s"$organizationClaim in selfcare token"))
    orgClaimsMap = orgClaims.asScala.toMap
    organizationId   <- orgClaimsMap.get("id").toTry(MissingClaim("id in organization in selfcare token"))
    organizationUUID <- organizationId.toString.toUUID.leftMap(_ =>
      MissingClaim(s"$organizationClaim wrong format in selfcare token")
    )
  } yield organizationUUID

}
