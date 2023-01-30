package it.pagopa.interop.backendforfrontend.service.types

import cats.syntax.all._
import it.pagopa.interop.attributeregistrymanagement.client.{model => AttributeManagement}
import it.pagopa.interop.backendforfrontend.error.BFFErrors.AttributeNotExists
import it.pagopa.interop.backendforfrontend.model.EServiceDescriptorState._
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagement}
import it.pagopa.interop.catalogprocess.client.model.EServiceTechnology.{REST, SOAP}
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}

import java.util.UUID
import scala.concurrent.Future

object CatalogProcessServiceTypes {

  type CatalogProcessESeed              = CatalogProcess.EServiceSeed
  type CatalogProcessETechnology        = CatalogProcess.EServiceTechnology
  type CatalogProcessAttributesSeed     = CatalogProcess.AttributesSeed
  type CatalogProcessAttributeSeed      = CatalogProcess.AttributeSeed
  type CatalogProcessAttributeValueSeed = CatalogProcess.AttributeValueSeed
  type CatalogProcessOldEService        = CatalogProcess.OldEService
  type CatalogProcessOldAttributes      = CatalogProcess.OldAttributes
  type CatalogProcessOldAttribute       = CatalogProcess.OldAttribute
  type CatalogProcessOldAttributeValue  = CatalogProcess.OldAttributeValue
  type CatalogProcessOrganization       = CatalogProcess.Organization
  type CatalogProcessEDescriptor        = CatalogProcess.EServiceDescriptor

  implicit class CatalogEServiceTechnologyConverter(private val est: EServiceTechnology) extends AnyVal {
    def toProcess: CatalogProcessETechnology = est match {
      case EServiceTechnology.REST => REST
      case EServiceTechnology.SOAP => SOAP
    }
  }

  implicit class CatalogAttributeValueConverter(private val a: CatalogAttributeValueSeed) extends AnyVal {
    def toProcess: CatalogProcessAttributeValueSeed =
      CatalogProcess.AttributeValueSeed(id = a.id, explicitAttributeVerification = a.explicitAttributeVerification)
  }

  implicit class CatalogAttributeSeedConverter(private val e: CatalogAttributeSeed) extends AnyVal {
    def toProcess: CatalogProcessAttributeSeed =
      CatalogProcess.AttributeSeed(single = e.single.map(_.toProcess), group = e.group.nested.map(_.toProcess).value)
  }

  implicit class CatalogAttributesSeedConverter(private val esa: CatalogAttributesSeed) extends AnyVal {
    def toProcess: CatalogProcessAttributesSeed = CatalogProcess.AttributesSeed(
      certified = esa.certified.map(_.toProcess),
      declared = esa.declared.map(_.toProcess),
      verified = esa.verified.map(_.toProcess)
    )
  }

  implicit class CatalogEServiceSeedConverter(private val es: EServiceSeed) extends AnyVal {
    def toProcess: CatalogProcessESeed = CatalogProcess.EServiceSeed(
      name = es.name,
      description = es.description,
      technology = es.technology.toProcess,
      attributes = es.attributes.toProcess
    )
  }

  implicit class CatalogOldAttributeValueConverter(private val coav: CatalogProcessOldAttributeValue) extends AnyVal {
    def toApi: OldAttributeValue = OldAttributeValue(
      id = coav.id,
      name = coav.name,
      description = coav.description,
      explicitAttributeVerification = coav.explicitAttributeVerification
    )
  }

  implicit class CatalogOldAttributeConverter(private val coa: CatalogProcessOldAttribute) extends AnyVal {
    def toApi: OldAttribute =
      OldAttribute(single = coa.single.map(_.toApi), group = coa.group.nested.map(_.toApi).value)
  }

  implicit class CatalogOldAttributesConverter(private val coas: CatalogProcessOldAttributes) extends AnyVal {
    def toApi: OldAttributes = OldAttributes(
      certified = coas.certified.map(_.toApi),
      declared = coas.declared.map(_.toApi),
      verified = coas.verified.map(_.toApi)
    )
  }

  implicit class CatalogOldEServiceOrganizationConverter(private val coeo: CatalogProcessOrganization) extends AnyVal {
    def toApi: Organization = Organization(id = coeo.id, name = coeo.name)
  }

  implicit class CatalogOldEServiceDescriptorConverter(private val coed: CatalogProcessEDescriptor) extends AnyVal {
    def toApi: EServiceDescriptor = EServiceDescriptor(
      id = coed.id,
      version = coed.version,
      description = coed.description,
      audience = coed.audience,
      voucherLifespan = coed.voucherLifespan,
      dailyCallsPerConsumer = coed.dailyCallsPerConsumer,
      dailyCallsTotal = coed.dailyCallsTotal,
      interface = coed.interface.map(_.toApi),
      docs = coed.docs.map(_.toApi),
      state = coed.state.toApi,
      agreementApprovalPolicy = coed.agreementApprovalPolicy.toApi,
      serverUrls = coed.serverUrls
    )
  }

  implicit class CatalogOldEServiceConverter(private val coes: CatalogProcessOldEService) extends AnyVal {
    def toApi: OldEService = OldEService(
      id = coes.id,
      producer = coes.producer.toApi,
      name = coes.name,
      description = coes.description,
      technology = coes.technology.toApi,
      attributes = coes.attributes.toApi,
      descriptors = coes.descriptors.map(_.toApi)
    )
  }

  implicit class EServiceDescriptorStateConverter(private val d: CatalogProcess.EServiceDescriptorState)
      extends AnyVal {
    def toApi: EServiceDescriptorState = d match {
      case CatalogProcess.EServiceDescriptorState.DRAFT      => DRAFT
      case CatalogProcess.EServiceDescriptorState.PUBLISHED  => PUBLISHED
      case CatalogProcess.EServiceDescriptorState.DEPRECATED => DEPRECATED
      case CatalogProcess.EServiceDescriptorState.SUSPENDED  => SUSPENDED
      case CatalogProcess.EServiceDescriptorState.ARCHIVED   => ARCHIVED
    }
  }

  implicit class EServiceDescriptorStateObjectConverter(private val d: CatalogProcess.EServiceDescriptorState.type)
      extends AnyVal {
    def fromApi(s: EServiceDescriptorState): CatalogProcess.EServiceDescriptorState = s match {
      case DRAFT      => CatalogProcess.EServiceDescriptorState.DRAFT
      case PUBLISHED  => CatalogProcess.EServiceDescriptorState.PUBLISHED
      case DEPRECATED => CatalogProcess.EServiceDescriptorState.DEPRECATED
      case SUSPENDED  => CatalogProcess.EServiceDescriptorState.SUSPENDED
      case ARCHIVED   => CatalogProcess.EServiceDescriptorState.ARCHIVED
    }
  }

  implicit class AttributesConverter(private val a: CatalogProcess.Attributes) extends AnyVal {
    def toManagement: CatalogManagement.Attributes =
      CatalogManagement.Attributes(
        certified = a.certified.map(_.toManagement),
        declared = a.declared.map(_.toManagement),
        verified = a.verified.map(_.toManagement)
      )
  }

  implicit class AttributeConverter(private val a: CatalogProcess.Attribute) extends AnyVal {
    def toManagement: CatalogManagement.Attribute =
      CatalogManagement.Attribute(
        single = a.single.map(_.toManagement),
        group = a.group.nested.map(_.toManagement).value
      )
  }

  implicit class AttributeValueConverter(private val a: CatalogProcess.AttributeValue) extends AnyVal {
    def toManagement: CatalogManagement.AttributeValue =
      CatalogManagement.AttributeValue(id = a.id, explicitAttributeVerification = a.explicitAttributeVerification)
  }

  implicit class AgreementApprovalPolicyWrapper(private val aap: CatalogProcess.AgreementApprovalPolicy)
      extends AnyVal {
    def toApi: AgreementApprovalPolicy = aap match {
      case CatalogProcess.AgreementApprovalPolicy.AUTOMATIC => AgreementApprovalPolicy.AUTOMATIC
      case CatalogProcess.AgreementApprovalPolicy.MANUAL    => AgreementApprovalPolicy.MANUAL
    }
  }

  implicit class EServiceDocWrapper(private val esd: CatalogProcess.EServiceDoc) extends AnyVal {
    def toApi: EServiceDoc =
      EServiceDoc(id = esd.id, name = esd.name, contentType = esd.contentType, prettyName = esd.prettyName)
  }

  implicit class EServiceDescriptorWrapper(private val esd: CatalogProcess.EServiceDescriptor) extends AnyVal {

    def toCompactDescriptor: CompactDescriptor = CompactDescriptor(id = esd.id, state = esd.state.toApi, esd.version)
  }
  implicit class EServiceTechnologyWrapper(private val est: CatalogProcess.EServiceTechnology) extends AnyVal {
    def toApi: EServiceTechnology = est match {
      case CatalogProcess.EServiceTechnology.REST => EServiceTechnology.REST
      case CatalogProcess.EServiceTechnology.SOAP => EServiceTechnology.SOAP
    }
  }

  final case class AttributeDetails(name: String, description: String)

  implicit class AttributesWrapper(private val eServiceAttributes: CatalogProcess.Attributes) extends AnyVal {

    def toApi(attributes: Seq[AttributeManagement.Attribute]): Future[EServiceAttributes] = {
      val attributeNames: Map[UUID, AttributeDetails] =
        attributes.map(attr => attr.id -> AttributeDetails(attr.name, attr.description)).toMap

      for {
        certified <- eServiceAttributes.certified.traverse(convertToApiAttribute(attributeNames))
        declared  <- eServiceAttributes.declared.traverse(convertToApiAttribute(attributeNames))
        verified  <- eServiceAttributes.verified.traverse(convertToApiAttribute(attributeNames))
      } yield EServiceAttributes(certified = certified, declared = declared, verified = verified)
    }.toFuture

    private def convertToApiAttribute(
      attributeNames: Map[UUID, AttributeDetails]
    )(attribute: CatalogProcess.Attribute): Either[AttributeNotExists, EServiceAttribute] =
      for {
        single <- attribute.single.traverse(convertToApiAttributeValue(attributeNames))
        group  <- attribute.group.nested.traverse(convertToApiAttributeValue(attributeNames))
      } yield EServiceAttribute(single = single, group = group.value)

    private def convertToApiAttributeValue(
      attributeNames: Map[UUID, AttributeDetails]
    )(value: CatalogProcess.AttributeValue): Either[AttributeNotExists, EServiceAttributeValue] =
      attributeNames
        .get(value.id)
        .toRight(AttributeNotExists(value.id))
        .map(attribute =>
          EServiceAttributeValue(
            id = value.id,
            name = attribute.name,
            description = attribute.description,
            explicitAttributeVerification = value.explicitAttributeVerification
          )
        )
  }

}
