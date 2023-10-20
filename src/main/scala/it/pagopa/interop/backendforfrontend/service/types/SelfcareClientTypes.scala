package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.backendforfrontend.error.BFFErrors.MissingSelfcareFields
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.commons.utils.TypeConversions.OptionOps
import it.pagopa.interop.selfcare.v2.client.{model => SelfcareClient}

import scala.concurrent.Future

object SelfcareClientTypes {

  implicit class ProductResourceConverter(private val p: SelfcareClient.ProductResource) extends AnyVal {
    def toApi: Future[SelfcareProduct] = {
      val selfcareProduct = for {
        id    <- p.id
        title <- p.title
      } yield SelfcareProduct(id = id, name = title)

      selfcareProduct.toFuture {
        val missingFields: String = List(
          p.id.fold(Option("id"))(_ => Option.empty[String]),
          p.title.fold(Option("title"))(_ => Option.empty[String])
        ).flatten
          .mkString(", ")
        MissingSelfcareFields(p.getClass.getName, missingFields)
      }
    }
  }

  implicit class SelfcareInstitutionConverter(private val i: SelfcareClient.InstitutionResource) extends AnyVal {
    def toApi: Future[SelfcareInstitution] = {
      val selfcareInstitution = for {
        id               <- i.id
        description      <- i.description
        userProductRoles <- i.userProductRoles
      } yield SelfcareInstitution(
        id = id,
        description = description,
        userProductRoles = userProductRoles,
        parent = i.rootParent.flatMap(_.description)
      )

      selfcareInstitution.toFuture {
        val missingFields: String = List(
          i.id.fold(Option("id"))(_ => Option.empty[String]),
          i.description.fold(Option("description"))(_ => Option.empty[String]),
          i.userProductRoles.fold(Option("userProductRoles"))(_ => Option.empty[String])
        ).flatten
          .mkString(", ")
        MissingSelfcareFields(i.getClass.getName, missingFields)
      }
    }
  }
}
