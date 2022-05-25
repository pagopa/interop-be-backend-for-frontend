package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.selfcare.partyprocess

object PartyProcessServiceTypes {
  type PartyProcessInvoker           = partyprocess.client.invoker.ApiInvoker
  type PartyProcessApiRequest[T]     = partyprocess.client.invoker.ApiRequest[T]
  type PartyProcessApiKeyValue       = partyprocess.client.invoker.ApiKeyValue
  type PartyProcessRelationshipInfo  = partyprocess.client.model.RelationshipInfo
  type PartyProcessPartyRole         = partyprocess.client.model.PartyRole
  type PartyProcessRelationshipState = partyprocess.client.model.RelationshipState
  type PartyProcessProductInfo       = partyprocess.client.model.ProductInfo
}
