package it.pagopa.interop.backendforfrontend.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.TenantProcessService
import it.pagopa.interop.tenantprocess.client.invoker.ApiInvoker
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.tenantprocess.client.api.{EnumsSerializers, TenantApi}
import it.pagopa.interop.tenantprocess.client.invoker.BearerToken
import it.pagopa.interop.tenantprocess.client.model.{
  DeclaredTenantAttributeSeed,
  ExternalId,
  SelfcareTenantSeed,
  Tenant,
  VerifiedTenantAttributeSeed
}

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}
import it.pagopa.interop.tenantprocess.client.invoker.ApiRequest
import akka.actor.typed.ActorSystem
import it.pagopa.interop.commons.utils.withHeaders
import it.pagopa.interop.tenantprocess.client.model.TenantDelta

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

  def selfcareUpsertTenant(origin: String, externalId: String, name: String)(selfcareId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Tenant] = withHeaders[Tenant] { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[Tenant] = api.selfcareUpsertTenant(
      xCorrelationId = correlationId,
      selfcareTenantSeed = SelfcareTenantSeed(ExternalId(origin, externalId), selfcareId, name),
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    invoker.invoke(request, s"Upserting Tenant $name ($origin, $externalId) with SelfcareId $selfcareId")
  }

  override def verifyVerifiedAttribute(tenantId: UUID, seed: VerifiedTenantAttributeSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Tenant] =
    withHeaders[Tenant] { (bearerToken, correlationId, ip) =>
      val request =
        api.verifyVerifiedAttribute(
          xCorrelationId = correlationId,
          tenantId = tenantId,
          verifiedTenantAttributeSeed = seed,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Verifying verified attribute ${seed.id} to $tenantId")
    }

  override def revokeVerifiedAttribute(tenantId: UUID, attributeId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Tenant] =
    withHeaders[Tenant] { (bearerToken, correlationId, ip) =>
      val request =
        api.revokeVerifiedAttribute(
          xCorrelationId = correlationId,
          tenantId = tenantId,
          attributeId = attributeId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Revoking verified attribute $attributeId to $tenantId")
    }

  override def updateTenant(tenantId: UUID, tenantDelta: TenantDelta)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders[Unit] { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[Tenant] =
      api.updateTenant(xCorrelationId = correlationId, xForwardedFor = ip, id = tenantId, tenantDelta = tenantDelta)(
        BearerToken(bearerToken)
      )
    invoker.invoke(request, s"Updating tenant with id $tenantId").map(_ => ())(blockingEc)
  }

  override def getTenant(tenantId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant] =
    withHeaders[Tenant] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Tenant] =
        api.getTenant(xCorrelationId = correlationId, xForwardedFor = ip, id = tenantId)(BearerToken(bearerToken))
      invoker.invoke(request, s"Getting tenant with id $tenantId")
    }

}
