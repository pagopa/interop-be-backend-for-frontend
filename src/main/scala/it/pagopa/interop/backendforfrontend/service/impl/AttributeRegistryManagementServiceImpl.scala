package it.pagopa.interop.backendforfrontend.service.impl

import cats.implicits.catsSyntaxOptionId
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.attributeregistrymanagement.client.invoker.BearerToken
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
import it.pagopa.interop.commons.utils.extractHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class AttributeRegistryManagementServiceImpl(invoker: AttributeRegistryManagementInvoker, api: AttributeApi)(
  implicit ec: ExecutionContext
) extends AttributeRegistryManagementService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getAttributes(
    search: Option[String]
  )(implicit contexts: Seq[(String, String)]): Future[AttributesResponse] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getAttributes(xCorrelationId = correlationId, xForwardedFor = ip)(BearerToken(bearerToken))
      result <- invoker.invoke(request, s"Loading attributes")
    } yield result
  }

  override def getAttributeById(attributeId: UUID)(implicit contexts: Seq[(String, String)]): Future[MgmtAttribute] =
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getAttributeById(xCorrelationId = correlationId, attributeId = attributeId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, s"Getting attribute by id $attributeId")
    } yield result

  override def getAttributeByOriginAndCode(origin: String, code: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[MgmtAttribute] =
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getAttributeByOriginAndCode(xCorrelationId = correlationId, origin, code, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, s"Getting attribute by origin = $origin and code = $code")
    } yield result

  override def createAttribute(
    seed: MgmtAttributeSeed
  )(implicit contexts: Seq[(String, String)]): Future[MgmtAttribute] =
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.createAttribute(xCorrelationId = correlationId, attributeSeed = seed, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, s"Creating attribute with seed $seed")
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
      result <- invoker.invoke(request, s"Retrieving attribute in bulk. IDs: ${ids.mkString("[", ",", "]")}")
    } yield result

}
