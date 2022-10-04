package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.tenantprocess.client.model.{DeclaredTenantAttributeSeed, Tenant}

import java.util.UUID
import scala.concurrent.Future

trait TenantProcessService {

  def addDeclaredAttribute(seed: DeclaredTenantAttributeSeed)(implicit contexts: Seq[(String, String)]): Future[Tenant]
  def revokeDeclaredAttribute(attributeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant]
  def selfcareUpsertTenant(origin: String, externalId: String)(selfcareId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Tenant]

}
