package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.backendforfrontend.service.model.{OnboardingData, Institution}
import it.pagopa.interop.backendforfrontend.model.{CompactUser, SelfcareProduct, SelfcareInstitution, User}
import it.pagopa.interop.selfcare.v2.client.{model => SelfcareClient}
import it.pagopa.interop.backendforfrontend.error.BFFErrors.SelfcareEntityNotFilled

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
    def toApi(id: UUID): CompactUser = {
      (ur.name, ur.surname) match {
        case (None, None) => CompactUser(userId = id, name = "Utente", familyName = ur.id.toString)
        case _            =>
          CompactUser(userId = id, name = ur.name.getOrElse(""), familyName = ur.surname.getOrElse(""))
      }
    }
  }

  implicit class UserResourceConverter(private val ur: SelfcareClient.UserResource) extends AnyVal {
    def toApi(tenantId: UUID): Either[Throwable, User] = for {
      id      <- ur.id.toRight(SelfcareEntityNotFilled(ur.getClass().getName(), "id"))
      name    <- ur.name.toRight(SelfcareEntityNotFilled(ur.getClass().getName(), "name"))
      surname <- ur.surname.toRight(SelfcareEntityNotFilled(ur.getClass().getName(), "surname"))
      roles   <- ur.roles.toRight(SelfcareEntityNotFilled(ur.getClass().getName(), "roles"))
    } yield User(userId = id, tenantId = tenantId, name = name, familyName = surname, roles = roles)
  }

  implicit class InstitutionConverter(private val inst: SelfcareClient.Institution) extends AnyVal {
    def toApi: Either[Throwable, Institution] = for {
      id             <- inst.id.toRight(SelfcareEntityNotFilled(inst.getClass().getName(), "id"))
      origin         <- inst.origin.toRight(SelfcareEntityNotFilled(inst.getClass().getName(), "origin"))
      originId       <- inst.originId.toRight(SelfcareEntityNotFilled(inst.getClass().getName(), "originId"))
      description    <- inst.description.toRight(SelfcareEntityNotFilled(inst.getClass().getName(), "description"))
      digitalAddress <- inst.digitalAddress.toRight(
        SelfcareEntityNotFilled(inst.getClass().getName(), "digitalAddress")
      )
      subUnitType    <- inst.subunitType.toRight(SelfcareEntityNotFilled(inst.getClass().getName(), "subunitType"))
    } yield Institution(
      id = id,
      origin = origin,
      originId = originId,
      description = description,
      digitalAddress = digitalAddress,
      subUnitType = subUnitType
    )
  }

  implicit class OnboardingsResponseConverter(private val onb: SelfcareClient.OnboardingsResponse) extends AnyVal {
    def toApi: Either[Throwable, OnboardingData] = for {
      data        <- onb.onboardings.toRight(SelfcareEntityNotFilled(onb.getClass().getName(), "OnboardingsResponse"))
      onboarding  <- data.headOption.toRight(SelfcareEntityNotFilled(data.getClass().getName(), "OnboardingResponse"))
      onboardedAt <- onboarding.createdAt.toRight(SelfcareEntityNotFilled(onboarding.getClass().getName(), "createdAt"))
    } yield OnboardingData(onboardedAt = onboardedAt)
  }
}
