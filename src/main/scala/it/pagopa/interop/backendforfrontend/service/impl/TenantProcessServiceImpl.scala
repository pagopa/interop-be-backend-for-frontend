package it.pagopa.interop.backendforfrontend.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.TenantProcessService
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.TenantProcessInvoker
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.tenantprocess.client.api.TenantApi
import it.pagopa.interop.tenantprocess.client.invoker.BearerToken
import it.pagopa.interop.tenantprocess.client.model.{DeclaredTenantAttributeSeed, Tenant}

import java.util.UUID
import scala.concurrent.Future

final case class TenantProcessServiceImpl(invoker: TenantProcessInvoker, api: TenantApi) extends TenantProcessService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def addDeclaredAttribute(
    seed: DeclaredTenantAttributeSeed
  )(implicit contexts: Seq[(String, String)]): Future[Tenant] =
    withHeaders[Tenant] { (bearerToken, correlationId, ip) =>
      val request =
        api.addDeclaredAttribute(
          xCorrelationId = correlationId,
          declaredTenantAttributeSeed = seed,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Adding declared attribute ${seed.id} to requester Tenant")
    }

  override def revokeDeclaredAttribute(attributeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant] =
    withHeaders[Tenant] { (bearerToken, correlationId, ip) =>
      val request =
        api.revokeDeclaredAttribute(xCorrelationId = correlationId, attributeId = attributeId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Revoking declared attribute $attributeId to requester Tenant")
    }
}
