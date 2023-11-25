package it.pagopa.interop.backendforfrontend.service.impl

import akka.actor.typed.ActorSystem
import it.pagopa.interop.commons.utils.withHeaders
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.TenantProcessService
import it.pagopa.interop.tenantprocess.client.invoker.ApiInvoker
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.tenantprocess.client.api.{EnumsSerializers, TenantApi}
import it.pagopa.interop.tenantprocess.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.tenantprocess.client.model.{
  DeclaredTenantAttributeSeed,
  ExternalId,
  SelfcareTenantSeed,
  Tenant,
  Tenants,
  VerifiedTenantAttributeSeed,
  UpdateVerifiedTenantAttributeSeed,
  MailSeed,
  ResourceId
}
import it.pagopa.interop.tenantprocess.client.invoker.ApiRequest
import it.pagopa.interop.backendforfrontend.error.BFFErrors.SelfcareNotFound

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future, ExecutionContext}
import java.time.OffsetDateTime

class TenantProcessServiceImpl(tenantprocessUrl: String, blockingEc: ExecutionContextExecutor)(implicit
  system: ActorSystem[_]
) extends TenantProcessService {

  val invoker: ApiInvoker           = ApiInvoker(EnumsSerializers.all, blockingEc)(system.classicSystem)
  val api: TenantApi                = TenantApi(tenantprocessUrl)
  implicit val ec: ExecutionContext = blockingEc

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def addDeclaredAttribute(
    seed: DeclaredTenantAttributeSeed
  )(implicit contexts: Seq[(String, String)]): Future[Tenant] =
    withHeaders[Tenant] { (bearerToken, correlationId) =>
      val request =
        api.addDeclaredAttribute(xCorrelationId = correlationId, declaredTenantAttributeSeed = seed)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Adding declared attribute ${seed.id} to requester Tenant")
    }

  override def revokeDeclaredAttribute(attributeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant] =
    withHeaders[Tenant] { (bearerToken, correlationId) =>
      val request =
        api.revokeDeclaredAttribute(xCorrelationId = correlationId, attributeId = attributeId)(BearerToken(bearerToken))
      invoker.invoke(request, s"Revoking declared attribute $attributeId to requester Tenant")
    }

  def selfcareUpsertTenant(
    origin: String,
    externalId: String,
    name: String,
    mailSeed: MailSeed,
    onboardedAt: OffsetDateTime
  )(selfcareId: String)(implicit contexts: Seq[(String, String)]): Future[ResourceId] = withHeaders[ResourceId] {
    (bearerToken, correlationId) =>
      val request: ApiRequest[ResourceId] = api.selfcareUpsertTenant(
        xCorrelationId = correlationId,
        selfcareTenantSeed = SelfcareTenantSeed(ExternalId(origin, externalId), selfcareId, name, mailSeed, onboardedAt)
      )(BearerToken(bearerToken))
      invoker.invoke(request, s"Upserting Tenant $name ($origin, $externalId) with SelfcareId $selfcareId")
  }

  override def verifyVerifiedAttribute(tenantId: UUID, seed: VerifiedTenantAttributeSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Tenant] =
    withHeaders[Tenant] { (bearerToken, correlationId) =>
      val request =
        api.verifyVerifiedAttribute(
          xCorrelationId = correlationId,
          tenantId = tenantId,
          verifiedTenantAttributeSeed = seed
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Verifying verified attribute ${seed.id} to $tenantId")
    }

  override def revokeVerifiedAttribute(tenantId: UUID, attributeId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Tenant] =
    withHeaders[Tenant] { (bearerToken, correlationId) =>
      val request =
        api.revokeVerifiedAttribute(xCorrelationId = correlationId, tenantId = tenantId, attributeId = attributeId)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Revoking verified attribute $attributeId to $tenantId")
    }

  override def getTenant(tenantId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant] =
    withHeaders[Tenant] { (bearerToken, correlationId) =>
      val request: ApiRequest[Tenant] =
        api.getTenant(xCorrelationId = correlationId, id = tenantId)(BearerToken(bearerToken))
      invoker.invoke(request, s"Getting tenant with id $tenantId")
    }

  override def getBySelfcareId(selfcareId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant] =
    withHeaders[Tenant] { (bearerToken, correlationId) =>
      val request: ApiRequest[Tenant] =
        api.getTenantBySelfcareId(xCorrelationId = correlationId, selfcareId = selfcareId)(BearerToken(bearerToken))
      invoker
        .invoke(request, s"Retrieving Tenant with selfcareId $selfcareId")
        .recoverWith { case err: ApiError[_] if err.code == 404 => Future.failed(SelfcareNotFound(selfcareId)) }
    }

  override def getTenants(name: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[Tenants] =
    withHeaders[Tenants] { (bearerToken, correlationId) =>
      val request: ApiRequest[Tenants] =
        api.getTenants(xCorrelationId = correlationId, name = name, limit = limit, offset = offset)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Getting tenants with name $name, limit $limit, offset $offset")
    }

  override def getProducers(name: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[Tenants] =
    withHeaders[Tenants] { (bearerToken, correlationId) =>
      val request: ApiRequest[Tenants] =
        api.getProducers(xCorrelationId = correlationId, name = name, limit = limit, offset = offset)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Getting producers with name $name, limit $limit, offset $offset")
    }

  override def getConsumers(name: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[Tenants] =
    withHeaders[Tenants] { (bearerToken, correlationId) =>
      val request: ApiRequest[Tenants] =
        api.getConsumers(xCorrelationId = correlationId, name = name, limit = limit, offset = offset)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Getting consumers with name $name, limit $limit, offset $offset")
    }

  override def updateVerifiedAttribute(tenantId: UUID, attributeId: UUID, seed: UpdateVerifiedTenantAttributeSeed)(
    implicit contexts: Seq[(String, String)]
  ): Future[Tenant] =
    withHeaders[Tenant] { (bearerToken, correlationId) =>
      val request: ApiRequest[Tenant] =
        api.updateVerifiedAttribute(
          xCorrelationId = correlationId,
          tenantId = tenantId,
          attributeId = attributeId,
          updateVerifiedTenantAttributeSeed = seed
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Updating verified attribute $attributeId to $tenantId")
    }

  override def addTenantMail(tenantId: UUID, seed: MailSeed)(implicit contexts: Seq[(String, String)]): Future[Unit] =
    withHeaders[Unit] { (bearerToken, correlationId) =>
      val request: ApiRequest[Unit] =
        api.addTenantMail(xCorrelationId = correlationId, tenantId = tenantId, mailSeed = seed)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Add mail to tenant $tenantId")
    }

  override def deleteTenantMail(tenantId: UUID, mailId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] =
    withHeaders[Unit] { (bearerToken, correlationId) =>
      val request: ApiRequest[Unit] =
        api.deleteTenantMail(xCorrelationId = correlationId, tenantId = tenantId, mailId = mailId)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Delete mail ${mailId} to $tenantId")
    }

}
