package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.selfcare.partyprocess

object PartyProcessServiceTypes {
  type PartyProcessRelationshipInfo  = partyprocess.client.model.RelationshipInfo
  type PartyProcessPartyRole         = partyprocess.client.model.PartyRole
  type PartyProcessRelationshipState = partyprocess.client.model.RelationshipState
  type PartyProcessProductInfo       = partyprocess.client.model.ProductInfo
  type PartyProcessAttribute         = partyprocess.client.model.Attribute
  type PartyProcessInstitution       = partyprocess.client.model.Institution
}
