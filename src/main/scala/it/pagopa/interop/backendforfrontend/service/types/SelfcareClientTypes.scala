package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.selfcare.v2.client.{model => SelfcareClient}

object SelfcareClientTypes {

  implicit class ProductResourceConverter(private val p: SelfcareClient.ProductResource) extends AnyVal {
    def toApi: SelfcareProduct = SelfcareProduct(id = p.id, name = p.title)
  }

  implicit class SelfcareInstitutionConverter(private val i: SelfcareClient.InstitutionResource) extends AnyVal {
    def toApi: SelfcareInstitution =
      SelfcareInstitution(id = i.id, description = i.description, userProductRoles = i.userProductRoles)
  }
}
