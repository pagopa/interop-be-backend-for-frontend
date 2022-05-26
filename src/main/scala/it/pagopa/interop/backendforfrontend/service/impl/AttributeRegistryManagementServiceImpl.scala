package it.pagopa.interop.backendforfrontend.service.impl

import com.typesafe.scalalogging.Logger
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.attributeregistrymanagement.client.invoker.BearerToken
import it.pagopa.interop.attributeregistrymanagement.client.model.AttributesResponse
import it.pagopa.interop.backendforfrontend.service.AttributeRegistryManagementService
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.AttributeRegistryManagementInvoker
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.extractHeaders
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps

import scala.concurrent.{ExecutionContext, Future}

final case class AttributeRegistryManagementServiceImpl(invoker: AttributeRegistryManagementInvoker, api: AttributeApi)(
  implicit ec: ExecutionContext
) extends AttributeRegistryManagementService {

  implicit val logger = Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getAttributes(
    search: Option[String]
  )(implicit contexts: Seq[(String, String)]): Future[AttributesResponse] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getAttributes(xCorrelationId = correlationId, xForwardedFor = ip)(BearerToken(bearerToken))
      result <- invoker.invoke(request, s"Loading certificated attributes")
    } yield result
  }
}
