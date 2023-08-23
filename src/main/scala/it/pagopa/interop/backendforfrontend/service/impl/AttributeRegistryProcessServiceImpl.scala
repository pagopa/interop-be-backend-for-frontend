package it.pagopa.interop.backendforfrontend.service.impl

import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.attributeregistryprocess.client.api.{AttributeApi, EnumsSerializers}
import it.pagopa.interop.attributeregistryprocess.client.invoker.{ApiInvoker, BearerToken}
import it.pagopa.interop.attributeregistryprocess.client.model.{
  AttributeKind,
  Attributes,
  Attribute,
  AttributeSeed,
  CertifiedAttributeSeed
}
import it.pagopa.interop.backendforfrontend.service.AttributeRegistryProcessService
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders

import scala.concurrent.{ExecutionContextExecutor, Future, ExecutionContext}
import java.util.UUID

class AttributeRegistryProcessServiceImpl(attributeRegistryProcessURL: String, blockingEc: ExecutionContextExecutor)(
  implicit system: ActorSystem[_]
) extends AttributeRegistryProcessService {

  val invoker: ApiInvoker = ApiInvoker(EnumsSerializers.all, blockingEc)(system.classicSystem)
  val api: AttributeApi   = AttributeApi(attributeRegistryProcessURL)

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getAttributes(
    name: Option[String],
    origin: Option[String],
    limit: Int,
    offset: Int,
    kinds: Seq[AttributeKind]
  )(implicit contexts: Seq[(String, String)]): Future[Attributes] = withHeaders[Attributes] {
    (bearerToken, correlationId, ip) =>
      val request = api.getAttributes(
        xCorrelationId = correlationId,
        limit = limit,
        offset = offset,
        kinds = kinds,
        xForwardedFor = ip,
        name = name,
        origin = origin
      )(BearerToken(bearerToken))
      invoker.invoke(request, s"Retrieving attributes")
  }

  def getAttributeByOriginAndCode(origin: String, code: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Attribute] = withHeaders[Attribute] { (bearerToken, correlationId, ip) =>
    val request =
      api.getAttributeByOriginAndCode(xCorrelationId = correlationId, origin = origin, code = code, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
    invoker.invoke(request, s"Retrieving attribute with origin $origin and code $code")
  }

  def getAttributeById(attributeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Attribute] =
    withHeaders[Attribute] { (bearerToken, correlationId, ip) =>
      val request = api.getAttributeById(xCorrelationId = correlationId, attributeId = attributeId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      invoker.invoke(request, s"Retrieving attribute with id $attributeId")
    }

  override def getBulkAttributes(
    ids: Seq[UUID]
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Seq[Attribute]] = {

    def getAttributesFrom(offset: Int): Future[Seq[Attribute]] =
      getBulkAttributes(requestBody = ids, limit = 50, offset = offset)
        .map(_.results)

    def go(start: Int)(as: Seq[Attribute]): Future[Seq[Attribute]] =
      getAttributesFrom(start).flatMap(attrs =>
        if (attrs.size < 50) Future.successful(as ++ attrs) else go(start + 50)(as ++ attrs)
      )

    go(0)(Nil)
  }

  def getBulkAttributes(requestBody: Seq[UUID], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[Attributes] = withHeaders[Attributes] { (bearerToken, correlationId, ip) =>
    val request = api.getBulkedAttributes(
      xCorrelationId = correlationId,
      requestBody = requestBody.map(_.toString),
      offset = offset,
      limit = limit,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    invoker.invoke(request, s"Retrieving attributes in bulk by id in [$requestBody]")
  }

  override def createCertifiedAttribute(
    attributeSeed: CertifiedAttributeSeed
  )(implicit contexts: Seq[(String, String)]): Future[Attribute] =
    withHeaders[Attribute] { (bearerToken, correlationId, ip) =>
      val request =
        api.createCertifiedAttribute(
          xCorrelationId = correlationId,
          certifiedAttributeSeed = attributeSeed,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Creating certified attribute with name ${attributeSeed.name}")
    }

  override def createDeclaredAttribute(
    attributeSeed: AttributeSeed
  )(implicit contexts: Seq[(String, String)]): Future[Attribute] =
    withHeaders[Attribute] { (bearerToken, correlationId, ip) =>
      val request =
        api.createDeclaredAttribute(xCorrelationId = correlationId, attributeSeed = attributeSeed, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Creating declared attribute with name ${attributeSeed.name}")
    }

  override def createVerifiedAttribute(
    attributeSeed: AttributeSeed
  )(implicit contexts: Seq[(String, String)]): Future[Attribute] =
    withHeaders[Attribute] { (bearerToken, correlationId, ip) =>
      val request =
        api.createVerifiedAttribute(xCorrelationId = correlationId, attributeSeed = attributeSeed, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Creating verified attribute with name ${attributeSeed.name}")
    }
}
