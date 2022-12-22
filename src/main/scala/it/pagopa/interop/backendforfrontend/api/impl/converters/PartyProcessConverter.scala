package it.pagopa.interop.backendforfrontend.api.impl.converters

import it.pagopa.interop.backendforfrontend.error.BFFErrors.MissingUserFields
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service.types.PartyProcessServiceTypes._
import it.pagopa.interop.commons.utils.TypeConversions.OptionOps
import it.pagopa.interop.selfcare.partyprocess
import it.pagopa.interop.selfcare.userregistry.client.model.UserResource

import scala.concurrent.Future

object PartyProcessConverter {
  def toPartyRole(role: String): Either[Throwable, PartyProcessPartyRole] =
    partyprocess.client.model.PartyRole.fromValue(role)

  def toRelationshipState(role: String): Either[Throwable, PartyProcessRelationshipState] =
    partyprocess.client.model.RelationshipState.fromValue(role)

  def toApiRelationshipInfo(
    user: UserResource,
    partyProcessRelationshipInfo: PartyProcessRelationshipInfo
  ): Future[RelationshipInfo] = {
    val relationshipInfo = for {
      name       <- user.name.map(_.value)
      familyName <- user.familyName.map(_.value)
      taxCode    <- user.fiscalCode
    } yield RelationshipInfo(
      id = partyProcessRelationshipInfo.id,
      from = partyProcessRelationshipInfo.from,
      to = partyProcessRelationshipInfo.to,
      name = name,
      familyName = familyName,
      taxCode = taxCode,
      role = toApiPartyRole(partyProcessRelationshipInfo.role),
      product = toApiProductInfo(partyProcessRelationshipInfo.product),
      state = toApiRelationshipState(partyProcessRelationshipInfo.state),
      createdAt = partyProcessRelationshipInfo.createdAt,
      updatedAt = partyProcessRelationshipInfo.updatedAt
    )

    relationshipInfo.toFuture {
      val missingUserFields: String = List(
        user.name.fold(Option("name"))(_ => Option.empty[String]),
        user.familyName.fold(Option("familyName"))(_ => Option.empty[String]),
        user.fiscalCode.fold(Option("fiscalCode"))(_ => Option.empty[String])
      ).flatten
        .mkString(", ")
      MissingUserFields(user.id.toString, missingUserFields)
    }
  }

  def toApiPartyRole(role: PartyProcessPartyRole): PartyRole = role match {
    case partyprocess.client.model.PartyRole.MANAGER      => PartyRole.MANAGER
    case partyprocess.client.model.PartyRole.DELEGATE     => PartyRole.DELEGATE
    case partyprocess.client.model.PartyRole.SUB_DELEGATE => PartyRole.SUB_DELEGATE
    case partyprocess.client.model.PartyRole.OPERATOR     => PartyRole.OPERATOR
  }

  def toApiRelationshipState(state: PartyProcessRelationshipState): RelationshipState = state match {
    case partyprocess.client.model.RelationshipState.PENDING   => RelationshipState.PENDING
    case partyprocess.client.model.RelationshipState.ACTIVE    => RelationshipState.ACTIVE
    case partyprocess.client.model.RelationshipState.SUSPENDED => RelationshipState.SUSPENDED
    case partyprocess.client.model.RelationshipState.DELETED   => RelationshipState.DELETED
    case partyprocess.client.model.RelationshipState.REJECTED  => RelationshipState.REJECTED
  }

  def toApiProductInfo(productInfo: PartyProcessProductInfo): ProductInfo =
    ProductInfo(id = productInfo.id, role = productInfo.role, createdAt = productInfo.createdAt)
}
