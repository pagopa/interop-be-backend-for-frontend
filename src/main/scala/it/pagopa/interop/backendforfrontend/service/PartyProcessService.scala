package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.backendforfrontend.service.types.PartyProcessServiceTypes.PartyProcessRelationshipInfo
import it.pagopa.interop.selfcare.partyprocess.client.model.{PartyRole, RelationshipState}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait PartyProcessService {
  def getRelationship(
    relationshipId: UUID
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[PartyProcessRelationshipInfo]

  def getUserInstitutionRelationships(
    institutionId: UUID,
    personId: Option[UUID],
    roles: Seq[PartyRole],
    states: Seq[RelationshipState],
    products: Seq[String],
    productRoles: Seq[String]
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Seq[PartyProcessRelationshipInfo]]
}
