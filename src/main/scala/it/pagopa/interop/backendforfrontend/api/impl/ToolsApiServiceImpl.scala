package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.HttpHeader
import cats.data.NonEmptyList
import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.authorizationmanagement.client.model.KeyWithClient
import it.pagopa.interop.backendforfrontend.api.ToolsApiService
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.BFFErrors._
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
import it.pagopa.interop.clientassertionvalidation.model.AssertionValidationResult
import it.pagopa.interop.clientassertionvalidation.{NimbusClientAssertionValidator, Validation}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getOrganizationIdFutureUUID
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.backendforfrontend.common.HeaderUtils._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class ToolsApiServiceImpl(
  authorizationManagementService: AuthorizationManagementService,
  agreementProcessService: AgreementProcessService,
  catalogProcessService: CatalogProcessService,
  purposeProcessService: PurposeProcessService
)(implicit ec: ExecutionContext)
    extends ToolsApiService {

  private val clientAssertionValidator =
    new NimbusClientAssertionValidator(ApplicationConfiguration.clientAssertionAudience)

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
        validation    <- validateClientAssertion(clientId, clientAssertion, clientAssertionType, grantType).toFuture
        keyWithClient <- getKeyWithClient(validation.clientAssertion.sub, validation.clientAssertion.kid)
        _             <- assertIsConsumer(requesterId, keyWithClient)
        eService      <- Future.traverse(validation.clientAssertion.purposeId.toList)(getEService).map(_.headOption)
        _             <- verifyClientAssertionSignature(keyWithClient, validation, eService).toFuture
        _             <- verifyPlatformState(keyWithClient, validation, eService).toFuture
      } yield successfulValidationResult(clientKind = keyWithClient.client.kind.toApi, eService = eService)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleTokenValidationError(s"Error validating token generation request", headers)(
        validateTokenGeneration200(headers)
      )
    }
  }

  private def validateClientAssertion(
    clientId: Option[String],
    clientAssertion: String,
    clientAssertionType: String,
    grantType: String
  ): Either[ClientAssertionValidationWrapper, AssertionValidationResult] =
    Validation
      .validateClientAssertion(clientId, clientAssertion, clientAssertionType, grantType)(clientAssertionValidator)
      .leftMap(handleValidationResults(clientKind = None, eService = None))

  private def getKeyWithClient(clientId: UUID, kid: String)(implicit
    ec: ExecutionContext,
    contexts: Seq[(String, String)]
  ): Future[KeyWithClient] =
    authorizationManagementService
      .getKeyWithClient(clientId, kid)
      .recoverWith { case ex: KidNotFound =>
        Future.failed(
          handleValidationResults(clientKind = None, eService = None)(
            NonEmptyList.one(ClientAssertionPublicKeyNotFound(ex.kid, ex.clientId))
          )
        )
      }

  private def verifyClientAssertionSignature(
    keyWithClient: KeyWithClient,
    validation: AssertionValidationResult,
    eService: Option[TokenGenerationValidationEService]
  ): Either[ClientAssertionValidationWrapper, Unit] = Validation
    .verifyClientAssertionSignature(keyWithClient, validation)(clientAssertionValidator)
    .leftMap(e =>
      handleValidationResults(clientKind = keyWithClient.client.kind.toApi.some, eService = eService)(
        NonEmptyList.one(e)
      )
    )

  private def verifyPlatformState(
    keyWithClient: KeyWithClient,
    validation: AssertionValidationResult,
    eService: Option[TokenGenerationValidationEService]
  ): Either[ClientAssertionValidationWrapper, Unit] = Validation
    .verifyPlatformState(keyWithClient.client, validation.clientAssertion)
    .leftMap(handleValidationResults(clientKind = keyWithClient.client.kind.toApi.some, eService = eService))

  private def assertIsConsumer(requesterId: UUID, keyWithClient: KeyWithClient): Future[Unit] =
    if (requesterId != keyWithClient.client.consumerId)
      Future.failed(OrganizationNotAllowed(keyWithClient.client.id))
    else Future.unit

  private def successfulValidationResult(
    clientKind: ClientKind,
    eService: Option[TokenGenerationValidationEService]
  ) = {
    val successfulStep =
      TokenGenerationValidationEntry(result = TokenGenerationValidationStepResult.PASSED, failures = Nil)

    TokenGenerationValidationResult(
      clientKind = clientKind.some,
      eservice = eService,
      steps = TokenGenerationValidationSteps(
        clientAssertionValidation = successfulStep,
        publicKeyRetrieve = successfulStep,
        clientAssertionSignatureVerification = successfulStep,
        platformStatesVerification = successfulStep
      )
    )
  }

  private def handleValidationResults(
    clientKind: Option[ClientKind],
    eService: Option[TokenGenerationValidationEService]
  )(errors: NonEmptyList[ClientAssertionValidationError]): ClientAssertionValidationWrapper = {
    val clientAssertionValidationErrors = errors.collect { case e: ClientAssertionValidationFailure => e }
    val keyRetrieveErrors               = errors.collect { case e: ClientAssertionPublicKeyNotFound => e }
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
        clientKind = clientKind,
        eservice = eService,
        steps = TokenGenerationValidationSteps(
          clientAssertionValidation = TokenGenerationValidationEntry(
            result = stepResult(Nil, clientAssertionValidationErrors),
            failures = clientAssertionValidationErrors.map(e => TokenGenerationValidationStepFailure(e.code, e.msg))
          ),
          publicKeyRetrieve = TokenGenerationValidationEntry(
            result = stepResult(clientAssertionValidationErrors, keyRetrieveErrors),
            failures = keyRetrieveErrors.map(e => TokenGenerationValidationStepFailure(e.code, e.msg))
          ),
          clientAssertionSignatureVerification = TokenGenerationValidationEntry(
            result = stepResult(clientAssertionValidationErrors ++ keyRetrieveErrors, clientAssertionSignatureErrors),
            failures = clientAssertionSignatureErrors.map(e => TokenGenerationValidationStepFailure(e.code, e.msg))
          ),
          platformStatesVerification = TokenGenerationValidationEntry(
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
      agreement      <- maybeAgreement.toFuture(AgreementNotFound(purpose.consumerId))
      descriptor     <- eService.descriptors
        .find(_.id == agreement.descriptorId)
        .toFuture(AgreementDescriptorNotFound(agreement.id))
    } yield TokenGenerationValidationEService(
      id = eService.id,
      descriptorId = agreement.descriptorId,
      version = descriptor.version,
      name = eService.name
    )
}
