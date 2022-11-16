package it.pagopa.interop.backendforfrontend.api.impl

import it.pagopa.interop.attributeregistrymanagement.client.model.Attribute
import it.pagopa.interop.backendforfrontend.service.types.TenantManagementServiceTypes.AdaptableTenantAttribute
import it.pagopa.interop.backendforfrontend.service.types.TenantManagementServiceTypes.AdaptableTenantAttribute._

import java.util.UUID

object Utils {

  def tenantAttributesToApi[DepAttribute, ApiAttribute](
    tenantAttributes: Seq[DepAttribute],
    registryAttributes: Seq[Attribute]
  )(implicit adaptable: AdaptableTenantAttribute[DepAttribute, ApiAttribute]): Seq[ApiAttribute] = {
    val registryMap = registryAttributes.map(a => (a.id, a)).toMap
    tenantAttributes.flatMap(tenantAttributeToApi(_, registryMap))
  }

  def tenantAttributeToApi[DepAttribute, ApiAttribute](
    tenantAttribute: DepAttribute,
    registryAttributesMap: Map[UUID, Attribute]
  )(implicit adaptable: AdaptableTenantAttribute[DepAttribute, ApiAttribute]): Option[ApiAttribute] =
    registryAttributesMap.get(tenantAttribute.id).map(ra => tenantAttribute.toApi(ra.name, ra.description))

}
