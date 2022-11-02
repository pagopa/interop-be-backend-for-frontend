package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.tenantprocess.client.{model => TenantProcess}

object TenantProcessServiceTypes {

  implicit class DeclaredTenantAttributeSeedConverter(private val seed: DeclaredTenantAttributeSeed) extends AnyVal {
    def toSeed: TenantProcess.DeclaredTenantAttributeSeed = TenantProcess.DeclaredTenantAttributeSeed(id = seed.id)
  }

  implicit class VerifiedTenantAttributeSeedConverter(private val seed: VerifiedTenantAttributeSeed) extends AnyVal {
    def toSeed: TenantProcess.VerifiedTenantAttributeSeed = TenantProcess.VerifiedTenantAttributeSeed(
      id = seed.id,
      renewal = seed.renewal.toSeed,
      expirationDate = seed.expirationDate
    )
  }

  implicit class TenantDeltaConverter(private val delta: TenantDelta) extends AnyVal {
    def toExternalModel(tenant: TenantProcess.Tenant): TenantProcess.TenantDelta = TenantProcess.TenantDelta(
      selfcareId = tenant.selfcareId,
      features = tenant.features,
      mails = TenantProcess.MailSeed(
        kind = TenantProcess.MailKind.CONTACT_EMAIL,
        address = delta.contactEmail,
        description = delta.description
      ) :: Nil
    )
  }

  implicit class VerificationRenewalConverter(private val v: VerificationRenewal) extends AnyVal {
    def toSeed: TenantProcess.VerificationRenewal = v match {
      case VerificationRenewal.REVOKE_ON_EXPIRATION => TenantProcess.VerificationRenewal.REVOKE_ON_EXPIRATION
      case VerificationRenewal.AUTOMATIC_RENEWAL    => TenantProcess.VerificationRenewal.AUTOMATIC_RENEWAL
    }
  }
}
