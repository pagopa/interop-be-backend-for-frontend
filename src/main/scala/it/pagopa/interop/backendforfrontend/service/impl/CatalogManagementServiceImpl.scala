package it.pagopa.interop.backendforfrontend.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}

import it.pagopa.interop.backendforfrontend.service.CatalogManagementService
import it.pagopa.interop.catalogmanagement.client.invoker.ApiInvoker
import it.pagopa.interop.catalogmanagement.client.api.{EServiceApi, EnumsSerializers}
import it.pagopa.interop.catalogmanagement.client.invoker.BearerToken
import it.pagopa.interop.catalogmanagement.client.model.EService
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}

import java.util.UUID
import scala.concurrent.Future
import akka.actor.typed.ActorSystem
import scala.concurrent.ExecutionContextExecutor

class CatalogManagementServiceImpl(catalogManagementUrl: String, blockingEc: ExecutionContextExecutor)(implicit
  system: ActorSystem[_]
) extends CatalogManagementService {

  val invoker: ApiInvoker = ApiInvoker(EnumsSerializers.all, blockingEc)(system.classicSystem)
  val api: EServiceApi    = EServiceApi(catalogManagementUrl)

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getEService(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService] =
    withHeaders[EService] { (bearerToken, correlationId, ip) =>
      val request =
        api.getEService(xCorrelationId = correlationId, eServiceId = eServiceId.toString, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Retrieving EService $eServiceId")
    }

}
