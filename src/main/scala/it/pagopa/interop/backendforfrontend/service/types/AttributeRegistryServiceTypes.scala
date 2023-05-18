package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop._
import it.pagopa.interop.backendforfrontend.model.AttributeKind.{CERTIFIED, DECLARED, VERIFIED}
import it.pagopa.interop.attributeregistryprocess.client.{model => AttributeProcess}
import it.pagopa.interop.attributeregistrymanagement.client.{model => AttributeModel}
import it.pagopa.interop.backendforfrontend.model.{
  Attribute,
  AttributeKind,
  AttributeSeed,
  CertifiedAttribute,
  CompactAttribute,
  DeclaredAttribute,
  VerifiedAttribute
}

object AttributeRegistryServiceTypes {
  type MgmtAttribute          = AttributeModel.Attribute
  type MgmtAttributesResponse = AttributeModel.AttributesResponse
  type MgmtAttributeKind      = AttributeModel.AttributeKind
  type MgmtAttributeSeed      = AttributeModel.AttributeSeed

  private def toModel(kind: MgmtAttributeKind): AttributeKind = kind match {
    case attributeregistrymanagement.client.model.AttributeKind.CERTIFIED => CERTIFIED
    case attributeregistrymanagement.client.model.AttributeKind.DECLARED  => DECLARED
    case attributeregistrymanagement.client.model.AttributeKind.VERIFIED  => VERIFIED
  }

  private def fromModel(kind: AttributeKind): MgmtAttributeKind = kind match {
    case CERTIFIED => attributeregistrymanagement.client.model.AttributeKind.CERTIFIED
    case DECLARED  => attributeregistrymanagement.client.model.AttributeKind.DECLARED
    case VERIFIED  => attributeregistrymanagement.client.model.AttributeKind.VERIFIED
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

  implicit class AttributeRegistryManagementConverter(private val attribute: MgmtAttribute) extends AnyVal {
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
    def toSeed: MgmtAttributeSeed = attributeregistrymanagement.client.model.AttributeSeed(
      code = seed.code,
      kind = fromModel(seed.kind),
      description = seed.description,
      origin = seed.origin,
      name = seed.name
    )
  }

}
