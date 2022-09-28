package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.tenantmanagement.client.model.Tenant
import it.pagopa.interop.tenantmanagement.client.invoker.ApiError

import java.util.UUID
import scala.concurrent.Future

trait TenantManagementService {

  def getTenant(tenantId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant]

  def getBySelfcareId(getBySelfcareId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant]

}

object TenantManagementService {
  def is404(ex: Throwable): Boolean = ex match {
    case ApiError(code, _, _, _, _) => 404 == code
    case _                          => false
  }
}
