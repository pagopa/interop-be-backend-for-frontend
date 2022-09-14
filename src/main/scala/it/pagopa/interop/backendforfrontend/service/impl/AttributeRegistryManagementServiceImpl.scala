package it.pagopa.interop.backendforfrontend.service.impl

import cats.implicits.catsSyntaxOptionId
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.attributeregistrymanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.attributeregistrymanagement.client.model.AttributesResponse
import it.pagopa.interop.backendforfrontend.service.AttributeRegistryManagementService
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.{
  AttributeRegistryManagementInvoker,
  MgmtAttribute,
  MgmtAttributeSeed,
  MgmtAttributesResponse
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.{
  GenericClientError,
  ResourceConflictError,
  ResourceNotFoundError,
  ThirdPartyCallError
}
import it.pagopa.interop.commons.utils.extractHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class AttributeRegistryManagementServiceImpl(invoker: AttributeRegistryManagementInvoker, api: AttributeApi)(
  implicit ec: ExecutionContext
) extends AttributeRegistryManagementService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private val replacementEntityId: String = "NoIdentifier"
  private val serviceName: String         = "attribute-registry"

  override def getAttributes(
    search: Option[String]
  )(implicit contexts: Seq[(String, String)]): Future[AttributesResponse] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getAttributes(xCorrelationId = correlationId, xForwardedFor = ip)(BearerToken(bearerToken))
      result <- invoker.invoke(request, s"Loading attributes", invocationRecovery(search))
    } yield result
  }

  override def getAttributeById(attributeId: UUID)(implicit contexts: Seq[(String, String)]): Future[MgmtAttribute] =
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getAttributeById(xCorrelationId = correlationId, attributeId = attributeId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(
        request,
        s"Getting attribute by id $attributeId",
        invocationRecovery(Some(s"Attribute $attributeId"))
      )
    } yield result

  override def getAttributeByOriginAndCode(origin: String, code: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[MgmtAttribute] =
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getAttributeByOriginAndCode(xCorrelationId = correlationId, origin, code, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(
        request,
        s"Getting attribute by origin = $origin and code = $code",
        invocationRecovery(Some(s"Attribute $origin/$code"))
      )
    } yield result

  override def createAttribute(
    seed: MgmtAttributeSeed
  )(implicit contexts: Seq[(String, String)]): Future[MgmtAttribute] =
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.createAttribute(xCorrelationId = correlationId, attributeSeed = seed, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, s"Creating attribute with seed $seed", invocationRecovery(Some(seed.name)))
    } yield result

  override def getBulkAttributes(
    ids: Seq[UUID]
  )(implicit contexts: Seq[(String, String)]): Future[MgmtAttributesResponse] =
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getBulkedAttributes(
        xCorrelationId = correlationId,
        ids = ids.mkString(",").some,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      result <- invoker.invoke(
        request,
        s"Retrieving attribute in bulk. IDs: ${ids.mkString("[", ",", "]")}",
        invocationRecovery(None)
      )
    } yield result

  private def invocationRecovery[T](
    entityId: Option[String]
  ): (ContextFieldsToLog, LoggerTakingImplicit[ContextFieldsToLog], String) => PartialFunction[Throwable, Future[T]] =
    (context, logger, msg) => {
      case ex @ ApiError(404, message, _, _, _)  =>
        logger.error(s"$msg. code > 404 - message > $message", ex)(context)
        Future.failed[T](ResourceNotFoundError(entityId.getOrElse(replacementEntityId)))
      case ex @ ApiError(409, message, _, _, _)  =>
        logger.error(s"$msg. code > 409 - message > $message", ex)(context)
        Future.failed[T](ResourceConflictError(entityId.getOrElse(replacementEntityId)))
      case ex @ ApiError(code, message, _, _, _) =>
        logger.error(s"$msg. code > $code - message > $message", ex)(context)
        Future.failed[T](ThirdPartyCallError(serviceName, ex.getMessage))
      case ex                                    =>
        logger.error(s"$msg. Error: ${ex.getMessage}", ex)(context)
        Future.failed[T](GenericClientError(ex.getMessage))
    }

}
