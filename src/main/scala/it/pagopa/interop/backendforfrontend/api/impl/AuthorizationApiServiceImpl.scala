package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.nimbusds.jwt.JWTClaimsSet
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.AuthorizationApiService
import it.pagopa.interop.backendforfrontend.model.{IdentityToken, Problem, SessionToken}
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.BFFErrors.CreateSessionTokenRequestError
import it.pagopa.interop.commons.jwt.model.RSA
import it.pagopa.interop.commons.jwt.service.{JWTReader, SessionTokenGenerator}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters.MapHasAsScala
import scala.util.{Failure, Success, Try}

final case class AuthorizationApiServiceImpl(jwtReader: JWTReader, sessionTokenGenerator: SessionTokenGenerator)
    extends AuthorizationApiService {

  private val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](LoggerFactory.getLogger(this.getClass))
  private final val admittedSessionClaims: Set[String]         = Set("uid", "organization")

  /**
    * Code: 200, Message: Session token requested, DataType: SessionToken
    * Code: 400, Message: Bad Request, DataType: Problem
    * Code: 401, Message: Not authorized, DataType: Problem
    */
  override def getSessionToken(identityToken: IdentityToken)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerSessionToken: ToEntityMarshaller[SessionToken],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val sessionToken: Try[SessionToken] = for {
      claims        <- jwtReader.getClaims(identityToken.identity_token)
      sessionClaims <- extractSessionClaims(claims)
      token         <- sessionTokenGenerator.generate(
        RSA,
        sessionClaims,
        ApplicationConfiguration.interopAudience,
        ApplicationConfiguration.interopIdIssuer,
        ApplicationConfiguration.interopTokenDuration
      )
    } yield SessionToken(token)

    sessionToken match {
      case Success(value) => getSessionToken200(value)
      case Failure(ex)    =>
        logger.error(s"Error while creating a session token for this request - ${ex.getMessage}")
        getSessionToken400(problemOf(StatusCodes.BadRequest, CreateSessionTokenRequestError(ex.getMessage)))
    }
  }

  private def extractSessionClaims(claims: JWTClaimsSet): Try[Map[String, AnyRef]] = Try {
    claims.getClaims.asScala.view.filterKeys(admittedSessionClaims.contains).toMap
  }
}
