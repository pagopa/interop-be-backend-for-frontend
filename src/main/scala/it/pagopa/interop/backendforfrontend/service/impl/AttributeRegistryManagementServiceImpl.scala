package it.pagopa.interop.backendforfrontend.service.impl

import akka.actor.typed.ActorSystem
import cats.implicits.catsSyntaxOptionId
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.client.api.EnumsSerializers
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.attributeregistrymanagement.client.invoker.{ApiInvoker, BearerToken}
import it.pagopa.interop.backendforfrontend.service.AttributeRegistryManagementService
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.{
  MgmtAttribute,
  MgmtAttributeSeed,
  MgmtAttributesResponse
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}

class AttributeRegistryManagementServiceImpl(attributeRegistryURL: String, blockingEc: ExecutionContextExecutor)(
  implicit system: ActorSystem[_]
) extends AttributeRegistryManagementService {

  val invoker: ApiInvoker = ApiInvoker(EnumsSerializers.all, blockingEc)(system.classicSystem)
  val api: AttributeApi   = AttributeApi(attributeRegistryURL)

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getAttributeById(attributeId: UUID)(implicit contexts: Seq[(String, String)]): Future[MgmtAttribute] =
    withHeaders[MgmtAttribute] { (bearerToken, correlationId, ip) =>
      val request = api.getAttributeById(xCorrelationId = correlationId, attributeId = attributeId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      invoker.invoke(request, s"Getting attribute by id ${attributeId.toString}")
    }

  override def getAttributeByOriginAndCode(origin: String, code: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[MgmtAttribute] = withHeaders[MgmtAttribute] { (bearerToken, correlationId, ip) =>
    val request = api.getAttributeByOriginAndCode(xCorrelationId = correlationId, origin, code, xForwardedFor = ip)(
      BearerToken(bearerToken)
    )
    invoker.invoke(request, s"Getting attribute by origin = $origin and code = $code")
  }

  override def createAttribute(seed: MgmtAttributeSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[MgmtAttribute] = withHeaders[MgmtAttribute] { (bearerToken, correlationId, ip) =>
    val request = api.createAttribute(xCorrelationId = correlationId, attributeSeed = seed, xForwardedFor = ip)(
      BearerToken(bearerToken)
    )
    invoker.invoke(request, s"Creating attribute with seed $seed")
  }

  override def getBulkAttributes(ids: Seq[UUID])(implicit
    contexts: Seq[(String, String)]
  ): Future[MgmtAttributesResponse] = withHeaders[MgmtAttributesResponse] { (bearerToken, correlationId, ip) =>
    val request =
      api.getBulkedAttributes(xCorrelationId = correlationId, ids = ids.mkString(",").some, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
    invoker.invoke(request, s"Retrieving attribute in bulk. IDs: ${ids.mkString("[", ",", "]")}")
  }

}
