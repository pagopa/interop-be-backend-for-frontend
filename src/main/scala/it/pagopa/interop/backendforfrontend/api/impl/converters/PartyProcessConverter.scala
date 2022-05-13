package it.pagopa.interop.backendforfrontend.api.impl.converters

import it.pagopa.interop.backendforfrontend.model.{PartyRole, ProductInfo, RelationshipInfo, RelationshipState}
import it.pagopa.interop.backendforfrontend.service.types.PartyProcessServiceTypes.{
  PartyProcessPartyRole,
  PartyProcessProductInfo,
  PartyProcessRelationshipInfo,
  PartyProcessRelationshipState
}
import it.pagopa.interop.commons.utils.TypeConversions.OptionOps
import it.pagopa.interop.selfcare.partyprocess
import it.pagopa.interop.selfcare.userregistry.client.model.UserResource

import scala.concurrent.Future

object PartyProcessConverter {
  def toPartyRole(role: String): Either[Throwable, PartyProcessPartyRole] = role match {
    case "MANAGER"      => Right(partyprocess.client.model.PartyRole.MANAGER)
    case "DELEGATE"     => Right(partyprocess.client.model.PartyRole.DELEGATE)
    case "SUB_DELEGATE" => Right(partyprocess.client.model.PartyRole.SUB_DELEGATE)
    case "OPERATOR"     => Right(partyprocess.client.model.PartyRole.OPERATOR)
    case _              => Left(new RuntimeException)

  }

  def toRelationshipState(role: String): Either[Throwable, PartyProcessRelationshipState] = role match {
    case "PENDING"   => Right(partyprocess.client.model.RelationshipState.PENDING)
    case "ACTIVE"    => Right(partyprocess.client.model.RelationshipState.ACTIVE)
    case "SUSPENDED" => Right(partyprocess.client.model.RelationshipState.SUSPENDED)
    case "DELETED"   => Right(partyprocess.client.model.RelationshipState.DELETED)
    case "REJECTED"  => Right(partyprocess.client.model.RelationshipState.REJECTED)
    case _           => Left(new RuntimeException)

  }

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

    relationshipInfo.toFuture(new RuntimeException)
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
