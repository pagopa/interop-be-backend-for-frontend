package it.pagopa.interop.backendforfrontend.service.impl

import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.attributeregistryprocess.client.api.{AttributeApi, EnumsSerializers}
import it.pagopa.interop.attributeregistryprocess.client.invoker.{ApiInvoker, BearerToken}
import it.pagopa.interop.attributeregistryprocess.client.model.{AttributeKind, Attributes}
import it.pagopa.interop.backendforfrontend.service.AttributeRegistryProcessService
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders

import scala.concurrent.{ExecutionContextExecutor, Future}

class AttributeRegistryProcessServiceImpl(attributeRegistryProcessURL: String, blockingEc: ExecutionContextExecutor)(
  implicit system: ActorSystem[_]
) extends AttributeRegistryProcessService {

  val invoker: ApiInvoker = ApiInvoker(EnumsSerializers.all, blockingEc)(system.classicSystem)
  val api: AttributeApi   = AttributeApi(attributeRegistryProcessURL)

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getAttributes(name: Option[String], limit: Int, offset: Int, kinds: Seq[AttributeKind])(implicit
    contexts: Seq[(String, String)]
  ): Future[Attributes] = withHeaders[Attributes] { (bearerToken, correlationId, ip) =>
    val request = api.getAttributes(
      xCorrelationId = correlationId,
      limit = limit,
      offset = offset,
      kinds = kinds,
      xForwardedFor = ip,
      name = name
    )(BearerToken(bearerToken))
    invoker.invoke(request, s"Retrieving attributes")
  }
}
