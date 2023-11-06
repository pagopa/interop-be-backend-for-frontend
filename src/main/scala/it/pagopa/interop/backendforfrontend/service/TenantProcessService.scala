package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.tenantprocess.client.model._

import java.util.UUID
import scala.concurrent.Future
import java.time.OffsetDateTime

trait TenantProcessService {

  def addTenantMail(tenantId: UUID, seed: MailSeed)(implicit contexts: Seq[(String, String)]): Future[Unit]
  def deleteTenantMail(tenantId: UUID, mailId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def addDeclaredAttribute(seed: DeclaredTenantAttributeSeed)(implicit contexts: Seq[(String, String)]): Future[Tenant]
  def revokeDeclaredAttribute(attributeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant]
  def selfcareUpsertTenant(
    origin: String,
    externalId: String,
    name: String,
    mailSeed: MailSeed,
    onboardedAt: OffsetDateTime
  )(selfcareId: String)(implicit contexts: Seq[(String, String)]): Future[Tenant]

  def verifyVerifiedAttribute(tenantId: UUID, seed: VerifiedTenantAttributeSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Tenant]
  def revokeVerifiedAttribute(tenantId: UUID, attributeId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Tenant]

  def getTenant(tenantId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant]

  def getTenants(name: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[Tenants]

  def getProducers(name: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[Tenants]
  def getConsumers(name: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[Tenants]

  def updateVerifiedAttribute(tenantId: UUID, attributeId: UUID, seed: UpdateVerifiedTenantAttributeSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Tenant]

  def getBySelfcareId(selfcareId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant]
}
