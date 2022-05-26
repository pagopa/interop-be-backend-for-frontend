package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import com.nimbusds.jwt.JWTClaimsSet
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.AuthorizationApiService
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.BFFErrors.CreateSessionTokenRequestError
import it.pagopa.interop.backendforfrontend.model.{IdentityToken, SessionToken}
import it.pagopa.interop.commons.jwt.model.EC
import it.pagopa.interop.commons.jwt.service.{JWTReader, SessionTokenGenerator}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions.TryOps
import it.pagopa.interop.commons.utils.{ORGANIZATION, UID}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.util.{Failure, Success, Try}

final case class AuthorizationApiServiceImpl(jwtReader: JWTReader, sessionTokenGenerator: SessionTokenGenerator)(
  implicit ec: ExecutionContext
) extends AuthorizationApiService {

  private val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private val admittedSessionClaims: Set[String] = Set(UID, ORGANIZATION)

  /**
   * Code: 200, Message: Session token requested, DataType: SessionToken
   */
  override def getSessionToken(identityToken: IdentityToken)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerSessionToken: ToEntityMarshaller[SessionToken]
  ): Route = {
    val result: Future[SessionToken] = for {
      claims        <- jwtReader.getClaims(identityToken.identity_token).toFuture
      sessionClaims <- extractSessionClaims(claims).toFuture
      token         <- sessionTokenGenerator.generate(
        EC,
        sessionClaims,
        ApplicationConfiguration.generatedJwtAudience,
        ApplicationConfiguration.generatedJwtIssuer,
        ApplicationConfiguration.generatedJwtDuration
      )
    } yield SessionToken(token)

    onComplete(result) {
      case Success(token) => getSessionToken200(token)
      case Failure(ex)    =>
        logger.error(s"Error while creating a session token for this request - ${ex.getMessage}")
        complete(
          StatusCodes.InternalServerError,
          problemOf(StatusCodes.InternalServerError, CreateSessionTokenRequestError)
        )
    }
  }

  private def extractSessionClaims(claims: JWTClaimsSet): Try[Map[String, AnyRef]] = Try {
    claims.getClaims.asScala.view.filterKeys(admittedSessionClaims.contains).toMap
  }
}
