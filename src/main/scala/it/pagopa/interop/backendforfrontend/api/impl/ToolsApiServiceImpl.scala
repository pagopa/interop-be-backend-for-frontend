package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.data.NonEmptyList
import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.authorizationmanagement.client.model.ClientKind
import it.pagopa.interop.backendforfrontend.api.ToolsApiService
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.BFFErrors.{
  ClientAssertionValidationWrapper,
  KidNotFound,
  OrganizationNotAllowed
}
import it.pagopa.interop.backendforfrontend.error.Handlers.handleTokenValidationError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service.types.AuthorizationManagementServiceTypes._
import it.pagopa.interop.backendforfrontend.service.{
  AgreementProcessService,
  AuthorizationManagementService,
  CatalogProcessService,
  PurposeProcessService
}
import it.pagopa.interop.clientassertionvalidation.Errors.{
  ClientAssertionSignatureVerificationFailure,
  ClientAssertionValidationError,
  ClientAssertionValidationFailure,
  PlatformStateVerificationFailure
}
import it.pagopa.interop.clientassertionvalidation.NimbusClientAssertionValidator
import it.pagopa.interop.clientassertionvalidation.Validation.{
  validateClientAssertion,
  verifyClientAssertionSignature,
  verifyPlatformState
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getOrganizationIdFutureUUID
import it.pagopa.interop.commons.utils.TypeConversions._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

// TODO gli step non dovrebbero essere una lista con enum, ma un oggetto con campi definiti

// TODO
final case class PublicKeyNotFound(kid: String, clientId: UUID)
    extends ClientAssertionValidationError("8099", s"Public key with kid $kid not found for client $clientId")

final case class ToolsApiServiceImpl(
  authorizationManagementService: AuthorizationManagementService,
  agreementProcessService: AgreementProcessService,
  catalogProcessService: CatalogProcessService,
  purposeProcessService: PurposeProcessService
)(implicit ec: ExecutionContext)
    extends ToolsApiService {

  private val clientAssertionValidator = new NimbusClientAssertionValidator(
    ApplicationConfiguration.clientAssertionAudience
  )

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def validateTokenGeneration(
    clientId: Option[String],
    clientAssertion: String,
    clientAssertionType: String,
    grantType: String
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerTokenGenerationValidationResult: ToEntityMarshaller[TokenGenerationValidationResult],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[TokenGenerationValidationResult] =
      for {
        requesterId   <- getOrganizationIdFutureUUID(contexts)
        validation    <- validateClientAssertion(clientId, clientAssertion, clientAssertionType, grantType)(
          clientAssertionValidator
        ).leftMap(handleValidationResults(clientKind = None, eService = None)).toFuture
        keyWithClient <- authorizationManagementService
          .getKeyWithClient(validation.clientAssertion.sub, validation.clientAssertion.kid)
          .recoverWith { case ex: KidNotFound =>
            Future.failed(
              handleValidationResults(clientKind = None, eService = None)(
                NonEmptyList.one(PublicKeyNotFound(ex.kid, ex.clientId))
              )
            )
          }
        _             <-
          if (requesterId != keyWithClient.client.consumerId)
            Future.failed(OrganizationNotAllowed(keyWithClient.client.id))
          else Future.unit
        eService      <- Future.traverse(validation.clientAssertion.purposeId.toList)(getEService).map(_.headOption)
        _             <- verifyClientAssertionSignature(keyWithClient, validation)(clientAssertionValidator)
          .leftMap(e =>
            handleValidationResults(clientKind = keyWithClient.client.kind.some, eService = eService)(
              NonEmptyList.one(e)
            )
          )
          .toFuture
        _             <- verifyPlatformState(keyWithClient.client, validation.clientAssertion)
          .leftMap(handleValidationResults(clientKind = keyWithClient.client.kind.some, eService = eService))
          .toFuture
      } yield TokenGenerationValidationResult(
        clientKind = keyWithClient.client.kind.toApi.some,
        eservice = eService,
        results = List(
          TokenGenerationValidationEntry(
            step = TokenGenerationValidationStep.CLIENT_ASSERTION_VALIDATION,
            result = TokenGenerationValidationStepResult.PASSED,
            failures = Nil
          ),
          TokenGenerationValidationEntry(
            step = TokenGenerationValidationStep.PUBLIC_KEY_RETRIEVE,
            result = TokenGenerationValidationStepResult.PASSED,
            failures = Nil
          ),
          TokenGenerationValidationEntry(
            step = TokenGenerationValidationStep.CLIENT_ASSERTION_SIGNATURE_VERIFICATION,
            result = TokenGenerationValidationStepResult.PASSED,
            failures = Nil
          ),
          TokenGenerationValidationEntry(
            step = TokenGenerationValidationStep.CLIENT_ASSERTION_VALIDATION,
            result = TokenGenerationValidationStepResult.PASSED,
            failures = Nil
          )
        )
      )

    onComplete(result)(
      handleTokenValidationError(s"Error validating token generation request")(validateTokenGeneration200)
    )
  }

  private def handleValidationResults(
    clientKind: Option[ClientKind],
    eService: Option[TokenGenerationValidationEService]
  )(errors: NonEmptyList[ClientAssertionValidationError]): ClientAssertionValidationWrapper = {
    val clientAssertionValidationErrors = errors.collect { case e: ClientAssertionValidationFailure => e }
    val keyRetrieveErrors               = errors.collect { case e: PublicKeyNotFound => e }
    val clientAssertionSignatureErrors  = errors.collect { case e: ClientAssertionSignatureVerificationFailure => e }
    val platformStateVerificationErrors = errors.collect { case e: PlatformStateVerificationFailure => e }

    def stepResult(
      previousStepsErrors: List[ClientAssertionValidationError],
      currentStepsErrors: List[ClientAssertionValidationError]
    ): TokenGenerationValidationStepResult =
      if (currentStepsErrors.nonEmpty) TokenGenerationValidationStepResult.FAILED
      else if (previousStepsErrors.nonEmpty) TokenGenerationValidationStepResult.SKIPPED
      else TokenGenerationValidationStepResult.PASSED

    ClientAssertionValidationWrapper(
      TokenGenerationValidationResult(
        clientKind = clientKind.map(_.toApi),
        eservice = eService,
        results = List(
          TokenGenerationValidationEntry(
            step = TokenGenerationValidationStep.CLIENT_ASSERTION_VALIDATION,
            result = stepResult(Nil, clientAssertionValidationErrors),
            failures = clientAssertionValidationErrors.map(e => TokenGenerationValidationStepFailure(e.code, e.msg))
          ),
          TokenGenerationValidationEntry(
            step = TokenGenerationValidationStep.PUBLIC_KEY_RETRIEVE,
            result = stepResult(clientAssertionValidationErrors, keyRetrieveErrors),
            failures = keyRetrieveErrors.map(e => TokenGenerationValidationStepFailure(e.code, e.msg))
          ),
          TokenGenerationValidationEntry(
            step = TokenGenerationValidationStep.CLIENT_ASSERTION_SIGNATURE_VERIFICATION,
            result = stepResult(clientAssertionValidationErrors ++ keyRetrieveErrors, clientAssertionSignatureErrors),
            failures = clientAssertionSignatureErrors.map(e => TokenGenerationValidationStepFailure(e.code, e.msg))
          ),
          TokenGenerationValidationEntry(
            step = TokenGenerationValidationStep.CLIENT_ASSERTION_VALIDATION,
            result = stepResult(
              clientAssertionValidationErrors ++ keyRetrieveErrors ++ clientAssertionSignatureErrors,
              platformStateVerificationErrors
            ),
            failures = platformStateVerificationErrors.map(e => TokenGenerationValidationStepFailure(e.code, e.msg))
          )
        )
      )
    )
  }

  private def getEService(
    purposeId: UUID
  )(implicit ec: ExecutionContext, contexts: Seq[(String, String)]): Future[TokenGenerationValidationEService] =
    for {
      purpose        <- purposeProcessService.getPurpose(purposeId)
      eService       <- catalogProcessService.getEServiceById(purpose.eserviceId)
      maybeAgreement <- agreementProcessService.getLatestAgreement(consumerId = purpose.consumerId, eService = eService)
      agreement      <- maybeAgreement.toFuture(new RuntimeException("")) // TODO
      descriptor <- eService.descriptors.find(_.id == agreement.descriptorId).toFuture(new RuntimeException("")) // TODO
    } yield TokenGenerationValidationEService(
      id = eService.id,
      descriptorId = agreement.descriptorId,
      version = descriptor.version,
      name = eService.name
    )
}
