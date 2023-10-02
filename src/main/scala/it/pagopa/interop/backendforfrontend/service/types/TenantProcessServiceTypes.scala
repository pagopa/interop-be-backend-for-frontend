package it.pagopa.interop.backendforfrontend.service.types

import cats.implicits.catsSyntaxOptionId
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.tenantprocess.client.{model => TenantProcess}
import it.pagopa.interop.tenantmanagement.model.tenant.PersistentCertifiedAttribute
import it.pagopa.interop.attributeregistryprocess.client.{model => AttributeRegistry}
import it.pagopa.interop.backendforfrontend.api.impl.Utils

import java.util.UUID

object TenantProcessServiceTypes {

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
      : AdaptableTenantAttribute[TenantProcess.CertifiedTenantAttribute, CertifiedTenantAttribute] =
      new AdaptableTenantAttribute[TenantProcess.CertifiedTenantAttribute, CertifiedTenantAttribute] {
        def id(attribute: TenantProcess.CertifiedTenantAttribute): UUID = attribute.id

        def toApi(
          attribute: TenantProcess.CertifiedTenantAttribute,
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
      : AdaptableTenantAttribute[TenantProcess.DeclaredTenantAttribute, DeclaredTenantAttribute] =
      new AdaptableTenantAttribute[TenantProcess.DeclaredTenantAttribute, DeclaredTenantAttribute] {
        def id(attribute: TenantProcess.DeclaredTenantAttribute): UUID = attribute.id

        def toApi(
          attribute: TenantProcess.DeclaredTenantAttribute,
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
      : AdaptableTenantAttribute[TenantProcess.VerifiedTenantAttribute, VerifiedTenantAttribute] =
      new AdaptableTenantAttribute[TenantProcess.VerifiedTenantAttribute, VerifiedTenantAttribute] {
        def id(attribute: TenantProcess.VerifiedTenantAttribute): UUID = attribute.id

        def toApi(
          attribute: TenantProcess.VerifiedTenantAttribute,
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

  implicit class DeclaredTenantAttributeSeedConverter(private val seed: DeclaredTenantAttributeSeed) extends AnyVal {
    def toSeed: TenantProcess.DeclaredTenantAttributeSeed = TenantProcess.DeclaredTenantAttributeSeed(id = seed.id)
  }

  implicit class VerifiedTenantAttributeSeedConverter(private val seed: VerifiedTenantAttributeSeed) extends AnyVal {
    def toSeed: TenantProcess.VerifiedTenantAttributeSeed =
      TenantProcess.VerifiedTenantAttributeSeed(id = seed.id, expirationDate = seed.expirationDate)
  }

  implicit class CertifiedTenantAttributeConverter(private val a: TenantProcess.CertifiedTenantAttribute)
      extends AnyVal {

    def toPersistent: PersistentCertifiedAttribute = PersistentCertifiedAttribute(
      id = a.id,
      assignmentTimestamp = a.assignmentTimestamp,
      revocationTimestamp = a.revocationTimestamp
    )

  }

  implicit class TenantDeltaConverter(private val delta: TenantDelta) extends AnyVal {
    def toExternalModel: TenantProcess.TenantDelta = TenantProcess.TenantDelta(mails =
      TenantProcess.MailSeed(
        kind = TenantProcess.MailKind.CONTACT_EMAIL,
        address = delta.contactEmail,
        description = delta.description
      ) :: Nil
    )
  }

  implicit class TenantVerifierConverter(private val v: TenantProcess.TenantVerifier) extends AnyVal {
    def toApi: TenantVerifier = TenantVerifier(
      id = v.id,
      verificationDate = v.verificationDate,
      expirationDate = v.expirationDate,
      extensionDate = v.extensionDate
    )
  }

  implicit class TenantRevokerConverter(private val v: TenantProcess.TenantRevoker) extends AnyVal {
    def toApi: TenantRevoker = TenantRevoker(
      id = v.id,
      verificationDate = v.verificationDate,
      expirationDate = v.expirationDate,
      extensionDate = v.extensionDate,
      revocationDate = v.revocationDate
    )
  }

  implicit class MailConverter(private val m: TenantProcess.Mail) extends AnyVal {
    def toApi: Mail = Mail(address = m.address, description = m.description)
  }

  implicit class ExternalIdConverter(private val e: TenantProcess.ExternalId) extends AnyVal {
    def toApi: ExternalId = ExternalId(origin = e.origin, value = e.value)
  }

  implicit class AttributeListConverter(private val as: Seq[TenantProcess.TenantAttribute]) extends AnyVal {
    def toApi(attributes: Seq[AttributeRegistry.Attribute]): TenantAttributes =
      Utils.enhanceTenantAttributes(as, attributes)
  }

  implicit class TenantFeatureWrapper(private val t: TenantProcess.TenantFeature) extends AnyVal {
    def toApi: TenantFeature = t match {
      case TenantProcess.TenantFeature(Some(certifier)) =>
        TenantFeature(certifier = Certifier(certifier.certifierId).some)
      case TenantProcess.TenantFeature(None)            => TenantFeature(certifier = None)
    }
  }

  implicit class TenantConverter(private val t: TenantProcess.Tenant) extends AnyVal {
    def toApi(selfcareUUID: Option[UUID], attributes: Seq[AttributeRegistry.Attribute]): Tenant = Tenant(
      id = t.id,
      selfcareId = selfcareUUID,
      externalId = t.externalId.toApi,
      createdAt = t.createdAt,
      updatedAt = t.updatedAt,
      name = t.name,
      attributes = t.attributes.toApi(attributes),
      contactMail = t.mails.find(_.kind == TenantProcess.MailKind.CONTACT_EMAIL).map(_.toApi),
      features = t.features.map(_.toApi)
    )
  }

  implicit class RenewalVerifiedTenantAttributeSeedConverter(private val seed: UpdateVerifiedTenantAttributeSeed)
      extends AnyVal {
    def toSeed: TenantProcess.UpdateVerifiedTenantAttributeSeed =
      TenantProcess.UpdateVerifiedTenantAttributeSeed(expirationDate = seed.expirationDate)
  }

  implicit class TenantKindConverter(private val t: TenantProcess.TenantKind) extends AnyVal {
    def toApi: TenantKind = t match {
      case TenantProcess.TenantKind.PA      => TenantKind.PA
      case TenantProcess.TenantKind.PRIVATE => TenantKind.PRIVATE
      case TenantProcess.TenantKind.GSP     => TenantKind.GSP
    }
  }
}
