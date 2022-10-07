package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.backendforfrontend.model.VerificationRenewal.{AUTOMATIC_RENEWAL, REVOKE_ON_EXPIRATION}
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.tenantmanagement.client.{model => TenantManagement}

object TenantManagementServiceTypes {

  implicit class DeclaredTenantAttributeConverter(private val attribute: TenantManagement.DeclaredTenantAttribute)
      extends AnyVal {
    def toApi(name: String, description: String): DeclaredTenantAttribute = DeclaredTenantAttribute(
      id = attribute.id,
      name = name,
      description = description,
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
    def toApi(name: String, description: String): VerifiedTenantAttribute = VerifiedTenantAttribute(
      id = attribute.id,
      name = name,
      description = description,
      assignmentTimestamp = attribute.assignmentTimestamp,
      verifiedBy = attribute.verifiedBy.map(_.toApi),
      revokedBy = attribute.revokedBy.map(_.toApi)
    )
  }

  implicit class VerificationRenewalConverter(private val v: TenantManagement.VerificationRenewal) extends AnyVal {
    def toApi: VerificationRenewal = v match {
      case TenantManagement.VerificationRenewal.REVOKE_ON_EXPIRATION => REVOKE_ON_EXPIRATION
      case TenantManagement.VerificationRenewal.AUTOMATIC_RENEWAL    => AUTOMATIC_RENEWAL
    }
  }

  implicit class TenantVerifierConverter(private val v: TenantManagement.TenantVerifier) extends AnyVal {
    def toApi: TenantVerifier = TenantVerifier(
      id = v.id,
      verificationDate = v.verificationDate,
      renewal = v.renewal.toApi,
      expirationDate = v.expirationDate,
      extensionDate = v.extensionDate
    )
  }

  implicit class TenantRevokerConverter(private val v: TenantManagement.TenantRevoker) extends AnyVal {
    def toApi: TenantRevoker = TenantRevoker(
      id = v.id,
      verificationDate = v.verificationDate,
      expirationDate = v.expirationDate,
      renewal = v.renewal.toApi,
      extensionDate = v.extensionDate,
      revocationDate = v.revocationDate
    )
  }
}
