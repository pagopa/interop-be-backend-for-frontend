package it.pagopa.interop.backendforfrontend.service.impl

import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.CatalogProcessService
import it.pagopa.interop.catalogprocess.client.api.{EnumsSerializers, ProcessApi}
import it.pagopa.interop.catalogprocess.client.invoker.{ApiInvoker, ApiRequest, BearerToken}
import it.pagopa.interop.catalogprocess.client.model.{EServiceDescriptorState, EServices}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders

import scala.concurrent.{ExecutionContextExecutor, Future}

class CatalogProcessServiceImpl(catalogProcessUrl: String, blockingEc: ExecutionContextExecutor)(implicit
  system: ActorSystem[_]
) extends CatalogProcessService {

  val invoker: ApiInvoker = ApiInvoker(EnumsSerializers.all, blockingEc)(system.classicSystem)
  val api: ProcessApi     = ProcessApi(catalogProcessUrl)

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getEServices(
    name: Option[String] = None,
    producersIds: Seq[String],
    states: Seq[EServiceDescriptorState],
    offset: Int,
    limit: Int
  )(implicit contexts: Seq[(String, String)]): Future[EServices] =
    withHeaders[EServices] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[EServices] = api.getEServices(
        name = name,
        producersIds = producersIds,
        states = states,
        offset = offset,
        limit = limit,
        xCorrelationId = correlationId,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      invoker.invoke(
        request,
        s"Retrieving EServices for name = $name, producersIds = $producersIds, states = $states, offset = $offset, limit = $limit,"
      )
    }

}
