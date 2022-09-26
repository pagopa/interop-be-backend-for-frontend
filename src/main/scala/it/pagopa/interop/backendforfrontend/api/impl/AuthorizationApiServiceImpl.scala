package it.pagopa.interop.backendforfrontend.api.impl

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

final case class AuthorizationApiServiceImpl(
  jwtReader: JWTReader,
  sessionTokenGenerator: SessionTokenGenerator,
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
      (sessionClaims, roles, organizationId) <- readJwt(identityToken).toFuture
      rateLimitStatus                        <- rateLimiter.rateLimiting(organizationId)
      token                                  <- sessionTokenGenerator.generate(
        SignatureAlgorithm.RSAPkcs1Sha256,
        sessionClaims ++ Map[String, AnyRef](USER_ROLES -> roles, ORGANIZATION_ID_CLAIM -> organizationId.toString),
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

  def readJwt(identityToken: IdentityToken): Try[(Map[String, AnyRef], String, UUID)] = for {
    claims         <- jwtReader.getClaims(identityToken.identity_token)
    sessionClaims  <- extractSessionClaims(claims)
    organizationId <- getOrganizationId(claims)
  } yield (sessionClaims, getUserRoles(claims).mkString(","), organizationId)

  private def extractSessionClaims(claims: JWTClaimsSet): Try[Map[String, AnyRef]] = Try {
    claims.getClaims.asScala.view.filterKeys(admittedSessionClaims.contains).toMap
  }

  private def getOrganizationId(claims: JWTClaimsSet): Try[UUID] = for {
    nullableOrgClaimsMap <- Try(claims.getJSONObjectClaim(organizationClaim))
      .leftMap(_ => MissingClaim(organizationClaim))
    orgClaims            <- Option(nullableOrgClaimsMap).toTry(MissingClaim(organizationClaim))
    orgClaimsMap = orgClaims.asScala.toMap
    organizationId   <- orgClaimsMap.get("id").toTry(MissingClaim("id in organization"))
    organizationUUID <- organizationId.toString.toUUID.leftMap(_ => MissingClaim(s"$organizationClaim wrong format"))
  } yield organizationUUID

}
