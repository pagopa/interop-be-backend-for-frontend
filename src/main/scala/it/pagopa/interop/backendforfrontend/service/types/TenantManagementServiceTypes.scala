package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop._
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.tenantmanagement.client.{model => TenantManagement}

object TenantManagementServiceTypes {

  implicit class DeclaredTenantAttributeConverter(private val attribute: TenantManagement.DeclaredTenantAttribute)
      extends AnyVal {
    def toApi(name: String): DeclaredTenantAttribute = DeclaredTenantAttribute(
      id = attribute.id,
      name = name,
      assignmentTimestamp = attribute.assignmentTimestamp,
      revocationTimestamp = attribute.revocationTimestamp
    )
  }

  implicit class CertifiedTenantAttributeConverter(private val attribute: TenantManagement.CertifiedTenantAttribute)
      extends AnyVal {
    def toApi(name: String): CertifiedTenantAttribute = CertifiedTenantAttribute(
      id = attribute.id,
      name = name,
      assignmentTimestamp = attribute.assignmentTimestamp,
      revocationTimestamp = attribute.revocationTimestamp
    )
  }

  implicit class VerifiedTenantAttributeConverter(private val attribute: TenantManagement.VerifiedTenantAttribute)
      extends AnyVal {
    def toApi(name: String): VerifiedTenantAttribute = VerifiedTenantAttribute(
      id = attribute.id,
      name = name,
      assignmentTimestamp = attribute.assignmentTimestamp,
      renewal = attribute.renewal.toApi,
      verifiedBy = attribute.verifiedBy.map(_.toApi),
      revokedBy = attribute.revokedBy.map(_.toApi)
    )
  }

}
