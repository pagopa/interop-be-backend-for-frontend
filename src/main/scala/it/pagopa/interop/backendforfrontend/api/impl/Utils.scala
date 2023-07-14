package it.pagopa.interop.backendforfrontend.api.impl

import cats.syntax.all._
import it.pagopa.interop.attributeregistryprocess.client.model.Attribute
import it.pagopa.interop.attributeregistryprocess.client.{model => AttributeRegistry}
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service.types.TenantProcessServiceTypes.AdaptableTenantAttribute
import it.pagopa.interop.backendforfrontend.service.types.TenantProcessServiceTypes.AdaptableTenantAttribute._
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}
import it.pagopa.interop.agreementprocess.client.{model => AgreementProcess}
import it.pagopa.interop.tenantprocess.client.{model => TenantProcess}

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

  def enhanceTenantAttributes(
    tenantAttributes: Seq[TenantProcess.TenantAttribute],
    registryAttributes: Seq[AttributeRegistry.Attribute]
  ): TenantAttributes = {
    val registryAttributesMap: Map[UUID, Attribute] = registryAttributes.fproductLeft(_.id).toMap

    val declareds: Seq[DeclaredTenantAttribute] = tenantAttributes.collect {
      case TenantProcess.TenantAttribute(Some(declared), None, None) =>
        Utils.tenantAttributeToApi(declared, registryAttributesMap)
    }.flattenOption

    val certifieds: Seq[CertifiedTenantAttribute] = tenantAttributes.collect {
      case TenantProcess.TenantAttribute(None, Some(certified), None) =>
        Utils.tenantAttributeToApi(certified, registryAttributesMap)
    }.flattenOption

    val verifieds: Seq[VerifiedTenantAttribute] = tenantAttributes.collect {
      case TenantProcess.TenantAttribute(None, None, Some(verified)) =>
        Utils.tenantAttributeToApi(verified, registryAttributesMap)
    }.flattenOption

    TenantAttributes(declareds, certifieds, verifieds)
  }

  def tenantAttributesIds(tenant: TenantProcess.Tenant): Seq[UUID] =
    tenant.attributes.mapFilter(_.verified.map(_.id)) ++
      tenant.attributes.mapFilter(_.certified.map(_.id)) ++
      tenant.attributes.mapFilter(_.declared.map(_.id))

  def canBeUpgraded(eService: CatalogProcess.EService, a: AgreementProcess.Agreement): Boolean =
    eService.descriptors.find(_.id == a.descriptorId).exists(isUpgradable(_, eService.descriptors))

  def isUpgradable(
    descriptor: CatalogProcess.EServiceDescriptor,
    descriptors: Seq[CatalogProcess.EServiceDescriptor]
  ): Boolean =
    descriptors
      .filter(_.version.toInt > descriptor.version.toInt)
      .exists(d =>
        d.state == CatalogProcess.EServiceDescriptorState.PUBLISHED ||
          d.state == CatalogProcess.EServiceDescriptorState.SUSPENDED
      )
}
