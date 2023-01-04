package it.pagopa.interop.backendforfrontend.api.impl

import cats.syntax.all._
import it.pagopa.interop.attributeregistrymanagement.client.model.Attribute
import it.pagopa.interop.backendforfrontend.service.types.TenantManagementServiceTypes.AdaptableTenantAttribute
import it.pagopa.interop.backendforfrontend.service.types.TenantManagementServiceTypes.AdaptableTenantAttribute._
import it.pagopa.interop.attributeregistrymanagement.client.{model => AttributeRegistry}
import it.pagopa.interop.tenantmanagement.client.{model => TenantManagement}

import java.util.UUID
import it.pagopa.interop.backendforfrontend.model._

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

  def enhanceTenantAttributes(
    tenantAttributes: Seq[TenantManagement.TenantAttribute],
    registryAttributes: Seq[AttributeRegistry.Attribute]
  ): TenantAttributes = {
    val registryAttributesMap: Map[UUID, Attribute] = registryAttributes.fproductLeft(_.id).toMap

    val declareds: Seq[DeclaredTenantAttribute] = tenantAttributes.collect {
      case TenantManagement.TenantAttribute(Some(declared), None, None) =>
        Utils.tenantAttributeToApi(declared, registryAttributesMap)
    }.flattenOption

    val certifieds: Seq[CertifiedTenantAttribute] = tenantAttributes.collect {
      case TenantManagement.TenantAttribute(None, Some(certified), None) =>
        Utils.tenantAttributeToApi(certified, registryAttributesMap)
    }.flattenOption

    val verifieds: Seq[VerifiedTenantAttribute] = tenantAttributes.collect {
      case TenantManagement.TenantAttribute(None, None, Some(verified)) =>
        Utils.tenantAttributeToApi(verified, registryAttributesMap)
    }.flattenOption

    TenantAttributes(declareds, certifieds, verifieds)
  }

  def tenantAttributesIds(tenant: TenantManagement.Tenant): Seq[UUID] =
    tenant.attributes.mapFilter(_.verified.map(_.id)) ++
      tenant.attributes.mapFilter(_.certified.map(_.id)) ++
      tenant.attributes.mapFilter(_.declared.map(_.id))

}
