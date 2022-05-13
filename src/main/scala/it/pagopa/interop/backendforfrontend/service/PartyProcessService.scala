package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.backendforfrontend.service.types.PartyProcessServiceTypes.{
  PartyProcessApiKeyValue,
  PartyProcessRelationshipInfo
}
import it.pagopa.interop.selfcare.partyprocess.client.model.{PartyRole, RelationshipState}

import java.util.UUID
import scala.concurrent.Future

trait PartyProcessService {
  def getRelationship(relationshipId: UUID)(xSelfCareUID: String)(implicit
    partyProcessApiKeyValue: PartyProcessApiKeyValue,
    contexts: Seq[(String, String)]
  ): Future[PartyProcessRelationshipInfo]

  def getUserInstitutionRelationships(
    institutionId: UUID,
    personId: Option[UUID],
    roles: Seq[PartyRole],
    states: Seq[RelationshipState],
    products: Seq[String],
    productRoles: Seq[String]
  )(xSelfCareUID: String)(implicit
    partyProcessApiKeyValue: PartyProcessApiKeyValue,
    contexts: Seq[(String, String)]
  ): Future[Seq[PartyProcessRelationshipInfo]]
}
