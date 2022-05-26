package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.attributeregistrymanagement
import it.pagopa.interop.backendforfrontend.model.AttributeKind.{CERTIFIED, DECLARED, VERIFIED}
import it.pagopa.interop.backendforfrontend.model.{Attribute, AttributeKind, AttributesResponse}

object AttributeRegistryServiceTypes {
  type AttributeRegistryManagementInvoker = attributeregistrymanagement.client.invoker.ApiInvoker
  type MgmtAttribute                      = attributeregistrymanagement.client.model.Attribute
  type MgmtAttributesResponse             = attributeregistrymanagement.client.model.AttributesResponse
  type MgmtAttributeKind                  = attributeregistrymanagement.client.model.AttributeKind

  implicit class Converter(private val mgmtAttributesResponse: MgmtAttributesResponse) extends AnyVal {
    def toResponse: AttributesResponse =
      AttributesResponse(attributes = mgmtAttributesResponse.attributes.map(toAttribute))

    private def toAttribute(attribute: MgmtAttribute) = Attribute(
      id = attribute.id,
      code = attribute.code,
      kind = toModel(attribute.kind),
      description = attribute.description,
      origin = attribute.origin,
      name = attribute.name,
      creationTime = attribute.creationTime
    )

    private def toModel(kind: MgmtAttributeKind): AttributeKind = kind match {
      case attributeregistrymanagement.client.model.AttributeKind.CERTIFIED => CERTIFIED
      case attributeregistrymanagement.client.model.AttributeKind.DECLARED  => DECLARED
      case attributeregistrymanagement.client.model.AttributeKind.VERIFIED  => VERIFIED
    }
  }
}
