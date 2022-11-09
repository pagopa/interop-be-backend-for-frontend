package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.backendforfrontend.model.EServiceDescriptorState._
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}

object CatalogProcessServiceTypes {

  implicit class EServiceDescriptorConverter(private val d: CatalogProcess.EServiceDescriptor) extends AnyVal {
    def toActiveDescriptor: ActiveDescriptor =
      ActiveDescriptor(id = d.id, state = d.state.toApi, version = d.version)
  }

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

}
