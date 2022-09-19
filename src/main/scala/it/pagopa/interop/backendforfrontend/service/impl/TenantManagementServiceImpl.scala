package it.pagopa.interop.backendforfrontend.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.TenantManagementService
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.TenantManagementInvoker
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.commons.utils.extractHeaders
import it.pagopa.interop.tenantmanagement.client.api.TenantApi
import it.pagopa.interop.tenantmanagement.client.invoker.BearerToken
import it.pagopa.interop.tenantmanagement.client.model.Tenant

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class TenantManagementServiceImpl(invoker: TenantManagementInvoker, api: TenantApi)(implicit
  ec: ExecutionContext
) extends TenantManagementService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getTenant(tenantId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant] =
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getTenant(xCorrelationId = correlationId, tenantId = tenantId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, s"Retrieving Tenant $tenantId")
    } yield result

}
