package it.pagopa.interop.backendforfrontend.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.TenantProcessService

import it.pagopa.interop.tenantprocess.client.invoker.ApiInvoker
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.tenantprocess.client.api.{TenantApi, EnumsSerializers}
import it.pagopa.interop.tenantprocess.client.invoker.BearerToken
import it.pagopa.interop.tenantprocess.client.model.Tenant

import java.util.UUID
import scala.concurrent.{Future, ExecutionContextExecutor}
import it.pagopa.interop.tenantprocess.client.invoker.ApiRequest
import akka.actor.typed.ActorSystem
import it.pagopa.interop.tenantprocess.client.model.{SelfcareTenantSeed, ExternalId, DeclaredTenantAttributeSeed}
import it.pagopa.interop.commons.utils.withHeaders

class TenantProcessServiceImpl(tenantprocessUrl: String, blockingEc: ExecutionContextExecutor)(implicit
  system: ActorSystem[_]
) extends TenantProcessService {

  val invoker: ApiInvoker = ApiInvoker(EnumsSerializers.all, blockingEc)(system.classicSystem)
  val api: TenantApi      = TenantApi(tenantprocessUrl)

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

  def selfcareUpsertTenant(origin: String, externalId: String)(selfcareId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Tenant] = withHeaders[Tenant] { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[Tenant] =
      api.selfcareUpsertTenant(
        xCorrelationId = correlationId,
        selfcareTenantSeed = SelfcareTenantSeed(ExternalId(origin, externalId), selfcareId.toString()),
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(request, s"Upserting Tenant ($origin, $externalId) with SelfcareId ${selfcareId.toString()}")
  }

}
