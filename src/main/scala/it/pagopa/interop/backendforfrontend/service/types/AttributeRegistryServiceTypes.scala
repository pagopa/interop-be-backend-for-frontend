package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.backendforfrontend.model.AttributeKind.{CERTIFIED, DECLARED, VERIFIED}
import it.pagopa.interop.attributeregistryprocess.client.{model => AttributeProcess}
import it.pagopa.interop.backendforfrontend.model.{
  Attribute,
  AttributeKind,
  AttributeSeed,
  CertifiedAttributeSeed,
  CertifiedAttribute,
  CompactAttribute,
  DeclaredAttribute,
  VerifiedAttribute
}

object AttributeRegistryServiceTypes {

  private def toModel(kind: AttributeProcess.AttributeKind): AttributeKind = kind match {
    case AttributeProcess.AttributeKind.CERTIFIED => CERTIFIED
    case AttributeProcess.AttributeKind.DECLARED  => DECLARED
    case AttributeProcess.AttributeKind.VERIFIED  => VERIFIED
  }

  implicit class AttributeKindProcessConverter(private val ak: AttributeKind) extends AnyVal {
    def toProcess: AttributeProcess.AttributeKind = ak match {
      case CERTIFIED => AttributeProcess.AttributeKind.CERTIFIED
      case DECLARED  => AttributeProcess.AttributeKind.DECLARED
      case VERIFIED  => AttributeProcess.AttributeKind.VERIFIED
    }
  }

  implicit class AttributeProcessConverter(private val attribute: AttributeProcess.Attribute) extends AnyVal {
    def toApi: CompactAttribute = CompactAttribute(id = attribute.id, name = attribute.name)
  }

  implicit class AttributeRegistryConverter(private val attribute: AttributeProcess.Attribute) extends AnyVal {
    def toAttribute: Attribute = Attribute(
      id = attribute.id,
      code = attribute.code,
      kind = toModel(attribute.kind),
      description = attribute.description,
      origin = attribute.origin,
      name = attribute.name,
      creationTime = attribute.creationTime
    )

    def toCertifiedAttribute: CertifiedAttribute =
      CertifiedAttribute(attribute.id, attribute.description, attribute.name, attribute.creationTime)

    def toDeclaredAttribute: DeclaredAttribute =
      DeclaredAttribute(attribute.id, attribute.description, attribute.name, attribute.creationTime)

    def toVerifiedAttribute: VerifiedAttribute =
      VerifiedAttribute(attribute.id, attribute.description, attribute.name, attribute.creationTime)
  }

  implicit class AttributeSeedConverter(private val seed: AttributeSeed) extends AnyVal {
    def toSeed: AttributeProcess.AttributeSeed =
      AttributeProcess.AttributeSeed(description = seed.description, name = seed.name)
  }

  implicit class CertifiedAttributeSeedConverter(private val seed: CertifiedAttributeSeed) extends AnyVal {
    def toSeed: AttributeProcess.CertifiedAttributeSeed =
      AttributeProcess.CertifiedAttributeSeed(description = seed.description, name = seed.name, code = seed.code)
  }

}
