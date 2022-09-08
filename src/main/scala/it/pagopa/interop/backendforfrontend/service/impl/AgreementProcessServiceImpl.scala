package it.pagopa.interop.backendforfrontend.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.client.api.AgreementApi
import it.pagopa.interop.agreementprocess.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.agreementprocess.client.model.{Agreement, AgreementPayload}
import it.pagopa.interop.backendforfrontend.service.AgreementProcessService
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.AgreementProcessInvoker
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.{GenericClientError, ThirdPartyCallError}
import it.pagopa.interop.commons.utils.extractHeaders

import scala.concurrent.{ExecutionContext, Future}

final case class AgreementProcessServiceImpl(invoker: AgreementProcessInvoker, api: AgreementApi)(implicit
  ec: ExecutionContext
) extends AgreementProcessService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private val serviceName: String = "attribute-registry"

  override def createAgreement(seed: AgreementPayload)(implicit contexts: Seq[(String, String)]): Future[Agreement] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.createAgreement(xCorrelationId = correlationId, agreementPayload = seed, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, s"Creating agreement with seed $seed", invocationRecovery())
    } yield result
  }

  private def invocationRecovery[T]()
    : (ContextFieldsToLog, LoggerTakingImplicit[ContextFieldsToLog], String) => PartialFunction[Throwable, Future[T]] =
    (context, logger, msg) => {
      case ex @ ApiError(code, message, _, _, _) =>
        logger.error(s"$msg. code > $code - message > $message", ex)(context)
        Future.failed[T](ThirdPartyCallError(serviceName, ex.getMessage))
      case ex                                    =>
        logger.error(s"$msg. Error: ${ex.getMessage}", ex)(context)
        Future.failed[T](GenericClientError(ex.getMessage))
    }

}
