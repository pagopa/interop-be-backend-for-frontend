package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.attributeregistrymanagement
import it.pagopa.interop.backendforfrontend.model.AttributeKind.{CERTIFIED, DECLARED, VERIFIED}
import it.pagopa.interop.backendforfrontend.model.{
  Attribute,
  AttributeKind,
  AttributesResponse,
  AttributeSeed,
  CertifiedAttribute
}

object AttributeRegistryServiceTypes {
  type AttributeRegistryManagementInvoker = attributeregistrymanagement.client.invoker.ApiInvoker
  type MgmtAttribute                      = attributeregistrymanagement.client.model.Attribute
  type MgmtAttributesResponse             = attributeregistrymanagement.client.model.AttributesResponse
  type MgmtAttributeKind                  = attributeregistrymanagement.client.model.AttributeKind
  type MgmtAttributeSeed                  = attributeregistrymanagement.client.model.AttributeSeed

  implicit class AttributesResponseConverter(private val mgmtAttributesResponse: MgmtAttributesResponse)
      extends AnyVal {
    def toResponse: AttributesResponse =
      AttributesResponse(attributes = mgmtAttributesResponse.attributes.map(_.toAttribute))
  }

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

  implicit class AttributeConverter(private val attribute: MgmtAttribute) extends AnyVal {
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
