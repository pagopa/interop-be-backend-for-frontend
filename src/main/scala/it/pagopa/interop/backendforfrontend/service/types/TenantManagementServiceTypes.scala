package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.backendforfrontend.model.VerificationRenewal.{AUTOMATIC_RENEWAL, REVOKE_ON_EXPIRATION}
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.tenantmanagement.client.{model => TenantManagement}

import java.util.UUID

object TenantManagementServiceTypes {

  trait AdaptableTenantAttribute[A, B] {
//    type Aux[A0, B0] = AdaptableTenantAttribute[A0,  B0] { type B = B0  }
//    type DepAttribute = A
//    type LocalAttribute = B

    def tenantAttributeToApi(a: A, name: String, description: String): B
    def getAttributeId(a: A): UUID
  }

  object AdaptableTenantAttribute {
    def tenantAttributeToApi[A, B](a: A, name: String, description: String)(implicit
      attribute: AdaptableTenantAttribute[A, B]
    ): B = attribute.tenantAttributeToApi(a, name, description)

    def getAttributeId[A, B](a: A)(implicit attribute: AdaptableTenantAttribute[A, B]): UUID =
      attribute.getAttributeId(a)

    implicit val certifiedAttribute
      : AdaptableTenantAttribute[TenantManagement.CertifiedTenantAttribute, CertifiedTenantAttribute] =
      new AdaptableTenantAttribute[TenantManagement.CertifiedTenantAttribute, CertifiedTenantAttribute] {
        def getAttributeId(attribute: TenantManagement.CertifiedTenantAttribute): UUID = attribute.id
        def tenantAttributeToApi(
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
        def getAttributeId(attribute: TenantManagement.DeclaredTenantAttribute): UUID = attribute.id
        def tenantAttributeToApi(
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
        def getAttributeId(attribute: TenantManagement.VerifiedTenantAttribute): UUID = attribute.id
        def tenantAttributeToApi(
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
    def toApi(name: String, description: String): CertifiedTenantAttribute = CertifiedTenantAttribute(
      id = attribute.id,
      name = name,
      description = description,
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
