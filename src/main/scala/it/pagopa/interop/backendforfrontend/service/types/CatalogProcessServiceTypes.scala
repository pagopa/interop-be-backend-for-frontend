package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.backendforfrontend.model.EServiceDescriptorState._
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagement}

object CatalogProcessServiceTypes {

  implicit class EServiceDescriptorStateConverter(private val d: CatalogProcess.EServiceDescriptorState)
      extends AnyVal {
    def toApi: EServiceDescriptorState = d match {
      case CatalogProcess.EServiceDescriptorState.DRAFT      => DRAFT
      case CatalogProcess.EServiceDescriptorState.PUBLISHED  => PUBLISHED
      case CatalogProcess.EServiceDescriptorState.DEPRECATED => DEPRECATED
      case CatalogProcess.EServiceDescriptorState.SUSPENDED  => SUSPENDED
      case CatalogProcess.EServiceDescriptorState.ARCHIVED   => ARCHIVED
    }
  }

  implicit class EServiceDescriptorStateObjectConverter(private val d: CatalogProcess.EServiceDescriptorState.type)
      extends AnyVal {
    def fromApi(s: EServiceDescriptorState): CatalogProcess.EServiceDescriptorState = s match {
      case DRAFT      => CatalogProcess.EServiceDescriptorState.DRAFT
      case PUBLISHED  => CatalogProcess.EServiceDescriptorState.PUBLISHED
      case DEPRECATED => CatalogProcess.EServiceDescriptorState.DEPRECATED
      case SUSPENDED  => CatalogProcess.EServiceDescriptorState.SUSPENDED
      case ARCHIVED   => CatalogProcess.EServiceDescriptorState.ARCHIVED
    }
  }

  implicit class AttributesConverter(private val a: CatalogProcess.Attributes) extends AnyVal {
    def toManagement: CatalogManagement.Attributes =
      CatalogManagement.Attributes(
        certified = a.certified.map(_.toManagement),
        declared = a.declared.map(_.toManagement),
        verified = a.verified.map(_.toManagement)
      )
  }

  implicit class AttributeConverter(private val a: CatalogProcess.Attribute) extends AnyVal {
    def toManagement: CatalogManagement.Attribute =
      CatalogManagement.Attribute(single = a.single.map(_.toManagement), group = a.group.map(_.map(_.toManagement)))
  }

  implicit class AttributeValueConverter(private val a: CatalogProcess.AttributeValue) extends AnyVal {
    def toManagement: CatalogManagement.AttributeValue =
      CatalogManagement.AttributeValue(id = a.id, explicitAttributeVerification = a.explicitAttributeVerification)
  }
}
