package it.pagopa.interop.backendforfrontend.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.TenantManagementService
import it.pagopa.interop.tenantmanagement.client.invoker.{ApiError, ApiInvoker, ApiRequest, BearerToken}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.tenantmanagement.client.api.{EnumsSerializers, TenantApi}
import it.pagopa.interop.tenantmanagement.client.model.Tenant
import it.pagopa.interop.commons.utils.TypeConversions._

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}
import akka.actor.typed.ActorSystem
import it.pagopa.interop.commons.utils.withHeaders

class TenantManagementServiceImpl(tenantManagementUrl: String, blockingEc: ExecutionContextExecutor)(implicit
  system: ActorSystem[_]
) extends TenantManagementService {

  val invoker: ApiInvoker = ApiInvoker(EnumsSerializers.all, blockingEc)(system.classicSystem)
  val api: TenantApi      = TenantApi(tenantManagementUrl)

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getTenant(tenantId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant] =
    withHeaders[Tenant] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Tenant] =
        api.getTenant(xCorrelationId = correlationId, tenantId = tenantId, xForwardedFor = ip)(BearerToken(bearerToken))
      invoker.invoke(request, s"Retrieving Tenant $tenantId")
    }

  override def getBySelfcareId(selfcareId: String)(implicit contexts: Seq[(String, String)]): Future[Tenant] =
    withHeaders[Tenant] { (bearerToken, correlationId, ip) =>
      selfcareId.toFutureUUID.flatMap { selfcareUUID =>
        val request: ApiRequest[Tenant] =
          api.getTenantBySelfcareId(xCorrelationId = correlationId, selfcareId = selfcareUUID, xForwardedFor = ip)(
            BearerToken(bearerToken)
          )
        invoker.invoke(
          request,
          s"Retrieving Tenant with selfcareId $selfcareId",
          (context, logger, message) => {
            case ex @ ApiError(code, apiMessage, response, throwable, _) if code == 404 =>
              logger.warn(s"$message - code > $code - message > $apiMessage - response > $response", throwable)(context)
              Future.failed(ex)
            case ex @ ApiError(code, apiMessage, response, throwable, _)                =>
              logger.error(s"$message FAILED. code > $code - message > $apiMessage - response > $response", throwable)(
                context
              )
              Future.failed(ex)
            case ex                                                                     =>
              logger.error(s"$message FAILED. Error: ${ex.getMessage}", ex)(context)
              Future.failed(ex)
          }
        )
      }(blockingEc)
    }

}
