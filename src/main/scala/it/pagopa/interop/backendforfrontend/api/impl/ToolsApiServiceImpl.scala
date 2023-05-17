package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.ToolsApiService
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.BFFErrors.OrganizationNotAllowed
import it.pagopa.interop.backendforfrontend.error.Handlers.handleTokenValidationError
import it.pagopa.interop.backendforfrontend.model.Problem
import it.pagopa.interop.backendforfrontend.service.AuthorizationManagementService
import it.pagopa.interop.clientassertionvalidation.Validation.{
  validateClientAssertion,
  verifyClientAssertionSignature,
  verifyPlatformState
}
import it.pagopa.interop.commons.jwt.service.impl.{DefaultClientAssertionValidator, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getOrganizationIdFutureUUID
import it.pagopa.interop.commons.utils.TypeConversions._

import scala.concurrent.{ExecutionContext, Future}

final case class ToolsApiServiceImpl(authorizationManagementService: AuthorizationManagementService)(implicit
  ec: ExecutionContext
) extends ToolsApiService {

  private val clientAssertionValidator = new DefaultClientAssertionValidator with PublicKeysHolder {
    var publicKeyset: Map[KID, SerializedKey]                                        = Map.empty
    override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
      getClaimsVerifier(audience = ApplicationConfiguration.clientAssertionAudience)
  }

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def validateTokenGeneration(
    clientId: Option[String],
    clientAssertion: String,
    clientAssertionType: String,
    grantType: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    val result: Future[Unit] =
      for {
        requesterId   <- getOrganizationIdFutureUUID(contexts)
        checker       <- validateClientAssertion(clientId, clientAssertion, clientAssertionType, grantType)(
          clientAssertionValidator
        ).toFuture
        keyWithClient <- authorizationManagementService.getKeyWithClient(checker.subject, checker.kid)
        _             <- Future
          .failed(OrganizationNotAllowed(keyWithClient.client.id))
          .whenA(requesterId != keyWithClient.client.consumerId)
        _             <- verifyClientAssertionSignature(keyWithClient, checker).toFuture
        _             <- verifyPlatformState(keyWithClient.client, checker).toFuture
      } yield ()

    onComplete(result) {
      handleTokenValidationError[Unit](s"Error validating token generation request")(_ => validateTokenGeneration204)
    }
  }
}
