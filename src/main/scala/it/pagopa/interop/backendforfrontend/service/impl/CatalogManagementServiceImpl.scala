package it.pagopa.interop.backendforfrontend.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.CatalogManagementService
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.CatalogManagementInvoker
import it.pagopa.interop.catalogmanagement.client.api.EServiceApi
import it.pagopa.interop.catalogmanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.catalogmanagement.client.model.EService
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.{
  GenericClientError,
  ResourceNotFoundError,
  ThirdPartyCallError
}
import it.pagopa.interop.commons.utils.extractHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class CatalogManagementServiceImpl(invoker: CatalogManagementInvoker, api: EServiceApi)(implicit
  ec: ExecutionContext
) extends CatalogManagementService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private val serviceName: String     = "catalog-management"
  private val missingEntityId: String = "NoIdentifier"

  override def getEService(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService] =
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getEService(xCorrelationId = correlationId, eServiceId = eServiceId.toString, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(
        request,
        s"Retrieving EService $eServiceId",
        invocationRecovery(Some(eServiceId.toString))
      )
    } yield result

  private def invocationRecovery[T](
    entityId: Option[String]
  ): (ContextFieldsToLog, LoggerTakingImplicit[ContextFieldsToLog], String) => PartialFunction[Throwable, Future[T]] =
    (context, logger, msg) => {
      case ex @ ApiError(code, message, _, _, _) if code == 404 =>
        logger.error(s"$msg. code > $code - message > $message", ex)(context)
        Future.failed[T](ResourceNotFoundError(entityId.getOrElse(missingEntityId)))
      case ex @ ApiError(code, message, _, _, _)                =>
        logger.error(s"$msg. code > $code - message > $message", ex)(context)
        Future.failed[T](ThirdPartyCallError(serviceName, ex.getMessage))
      case ex                                                   =>
        logger.error(s"$msg. Error: ${ex.getMessage}", ex)(context)
        Future.failed[T](GenericClientError(ex.getMessage))
    }

}
