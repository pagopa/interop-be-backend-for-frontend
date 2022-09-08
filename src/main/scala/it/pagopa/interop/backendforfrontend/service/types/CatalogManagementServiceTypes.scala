package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagement}
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.model.EServiceDescriptorState._

object CatalogManagementServiceTypes {

  implicit class EServiceDescriptorConverter(private val d: CatalogManagement.EServiceDescriptor) extends AnyVal {
    def toActiveDescriptor: ActiveDescriptor =
      ActiveDescriptor(id = d.id, state = d.state.toApi, version = d.version)
  }

  implicit class EServiceDescriptorStateConverter(private val d: CatalogManagement.EServiceDescriptorState)
      extends AnyVal {
    def toApi: EServiceDescriptorState = d match {
      case CatalogManagement.EServiceDescriptorState.DRAFT      => DRAFT
      case CatalogManagement.EServiceDescriptorState.PUBLISHED  => PUBLISHED
      case CatalogManagement.EServiceDescriptorState.DEPRECATED => DEPRECATED
      case CatalogManagement.EServiceDescriptorState.SUSPENDED  => SUSPENDED
      case CatalogManagement.EServiceDescriptorState.ARCHIVED   => ARCHIVED
    }
  }

}
