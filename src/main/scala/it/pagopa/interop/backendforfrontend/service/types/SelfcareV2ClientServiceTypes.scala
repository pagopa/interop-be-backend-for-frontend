package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.backendforfrontend.service.model.Institution
import it.pagopa.interop.backendforfrontend.model.{TenantUser, SelfcareProduct, SelfcareInstitution, User}
import it.pagopa.interop.selfcare.v2.client.{model => SelfcareClient}
import it.pagopa.interop.backendforfrontend.error.BFFErrors.SelfcareEntityNotFilled
import it.pagopa.interop.commons.utils.TypeConversions._

import java.util.UUID

object SelfcareV2ClientServiceTypes {

  implicit class ProductResourceConverter(private val p: SelfcareClient.ProductResource) extends AnyVal {
    def toApi: Either[Throwable, SelfcareProduct] = for {
      id    <- p.id.toRight(SelfcareEntityNotFilled(p.getClass().getName(), "id"))
      title <- p.title.toRight(SelfcareEntityNotFilled(p.getClass().getName(), "title"))
    } yield SelfcareProduct(id = id, name = title)
  }

  implicit class SelfcareInstitutionConverter(private val i: SelfcareClient.InstitutionResource) extends AnyVal {
    def toApi: Either[Throwable, SelfcareInstitution] = for {
      id               <- i.id.toRight(SelfcareEntityNotFilled(i.getClass().getName(), "id"))
      description      <- i.description.toRight(SelfcareEntityNotFilled(i.getClass().getName(), "description"))
      userProductRoles <- i.userProductRoles.toRight(
        SelfcareEntityNotFilled(i.getClass().getName(), "userProductRoles")
      )
    } yield SelfcareInstitution(id = id, description = description, userProductRoles = userProductRoles)
  }

  implicit class SelfcareUserResponseConverter(private val ur: SelfcareClient.UserResponse) extends AnyVal {
    def toApi: Either[Throwable, User] = for {
      id      <- ur.id.toRight(SelfcareEntityNotFilled(ur.getClass().getName(), "id"))
      uuid    <- id.toUUID.toEither
      name    <- ur.name.toRight(SelfcareEntityNotFilled(ur.getClass().getName(), "name"))
      surname <- ur.surname.toRight(SelfcareEntityNotFilled(ur.getClass().getName(), "surname"))
    } yield User(userId = uuid, name = name, surname = surname)

    def toApi(id: UUID): User = User(userId = id, name = ur.name.getOrElse(""), surname = ur.surname.getOrElse(""))
  }

  implicit class UserResourceConverter(private val ur: SelfcareClient.UserResource) extends AnyVal {
    def toApi(tenantId: UUID): Either[Throwable, TenantUser] = for {
      id         <- ur.id.toRight(SelfcareEntityNotFilled(ur.getClass().getName(), "id"))
      name       <- ur.name.toRight(SelfcareEntityNotFilled(ur.getClass().getName(), "name"))
      surname    <- ur.surname.toRight(SelfcareEntityNotFilled(ur.getClass().getName(), "surname"))
      fiscalCode <- ur.fiscalCode.toRight(SelfcareEntityNotFilled(ur.getClass().getName(), "fiscalCode"))
      roles      <- ur.roles.toRight(SelfcareEntityNotFilled(ur.getClass().getName(), "roles"))
    } yield TenantUser(
      userId = id,
      tenantId = tenantId,
      name = name,
      familyName = surname,
      taxCode = fiscalCode,
      roles = roles
    )
  }

  implicit class InstitutionConverter(private val inst: SelfcareClient.Institution) extends AnyVal {
    def toApi: Either[Throwable, Institution] = for {
      id          <- inst.id.toRight(SelfcareEntityNotFilled(inst.getClass().getName(), "id"))
      origin      <- inst.origin.toRight(SelfcareEntityNotFilled(inst.getClass().getName(), "origin"))
      originId    <- inst.originId.toRight(SelfcareEntityNotFilled(inst.getClass().getName(), "originId"))
      description <- inst.description.toRight(SelfcareEntityNotFilled(inst.getClass().getName(), "description"))
    } yield Institution(id = id, origin = origin, originId = originId, description = description)
  }
}
