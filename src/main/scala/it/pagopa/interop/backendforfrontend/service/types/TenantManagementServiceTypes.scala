package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.backendforfrontend.model.VerificationRenewal.{AUTOMATIC_RENEWAL, REVOKE_ON_EXPIRATION}
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.tenantmanagement.client.{model => TenantManagement}

import java.util.UUID

object TenantManagementServiceTypes {

  trait AdaptableTenantAttribute[DepAttribute, ApiAttribute] {
    def toApi(a: DepAttribute, name: String, description: String): ApiAttribute
    def id(a: DepAttribute): UUID
  }

  object AdaptableTenantAttribute {
    def apply[DepAttribute, ApiAttribute](implicit
      attribute: AdaptableTenantAttribute[DepAttribute, ApiAttribute]
    ): AdaptableTenantAttribute[DepAttribute, ApiAttribute] = attribute

    implicit class AdaptableTenantAttributeOps[DepAttribute, ApiAttribute](a: DepAttribute)(implicit
      attribute: AdaptableTenantAttribute[DepAttribute, ApiAttribute]
    ) {
      def toApi(name: String, description: String): ApiAttribute =
        AdaptableTenantAttribute[DepAttribute, ApiAttribute].toApi(a, name, description)
      def id: UUID = AdaptableTenantAttribute[DepAttribute, ApiAttribute].id(a)
    }

    implicit val certifiedAttribute
      : AdaptableTenantAttribute[TenantManagement.CertifiedTenantAttribute, CertifiedTenantAttribute] =
      new AdaptableTenantAttribute[TenantManagement.CertifiedTenantAttribute, CertifiedTenantAttribute] {
        def id(attribute: TenantManagement.CertifiedTenantAttribute): UUID = attribute.id
        def toApi(
          attribute: TenantManagement.CertifiedTenantAttribute,
          name: String,
          description: String
        ): CertifiedTenantAttribute =
          CertifiedTenantAttribute(
            id = attribute.id,
            name = name,
            description = description,
            assignmentTimestamp = attribute.assignmentTimestamp,
            revocationTimestamp = attribute.revocationTimestamp
          )
      }

    implicit val declaredAttribute
      : AdaptableTenantAttribute[TenantManagement.DeclaredTenantAttribute, DeclaredTenantAttribute] =
      new AdaptableTenantAttribute[TenantManagement.DeclaredTenantAttribute, DeclaredTenantAttribute] {
        def id(attribute: TenantManagement.DeclaredTenantAttribute): UUID = attribute.id
        def toApi(
          attribute: TenantManagement.DeclaredTenantAttribute,
          name: String,
          description: String
        ): DeclaredTenantAttribute =
          DeclaredTenantAttribute(
            id = attribute.id,
            name = name,
            description = description,
            assignmentTimestamp = attribute.assignmentTimestamp,
            revocationTimestamp = attribute.revocationTimestamp
          )
      }

    implicit val verifiedAttribute
      : AdaptableTenantAttribute[TenantManagement.VerifiedTenantAttribute, VerifiedTenantAttribute] =
      new AdaptableTenantAttribute[TenantManagement.VerifiedTenantAttribute, VerifiedTenantAttribute] {
        def id(attribute: TenantManagement.VerifiedTenantAttribute): UUID = attribute.id
        def toApi(
          attribute: TenantManagement.VerifiedTenantAttribute,
          name: String,
          description: String
        ): VerifiedTenantAttribute =
          VerifiedTenantAttribute(
            id = attribute.id,
            name = name,
            description = description,
            assignmentTimestamp = attribute.assignmentTimestamp,
            verifiedBy = attribute.verifiedBy.map(_.toApi),
            revokedBy = attribute.revokedBy.map(_.toApi)
          )
      }

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

  implicit class MailConverter(private val m: TenantManagement.Mail) extends AnyVal {
    def toApi: Mail = Mail(address = m.address, description = m.description)
  }
}
