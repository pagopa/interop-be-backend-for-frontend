package it.pagopa.interop.backendforfrontend.service.types

import cats.syntax.all._
import it.pagopa.interop.attributeregistrymanagement.client.{model => AttributeManagement}
import it.pagopa.interop.backendforfrontend.error.BFFErrors.AttributeNotExists
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagement}
import it.pagopa.interop.catalogprocess.client.model.EServiceTechnology.{REST, SOAP}
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}

import java.util.UUID
import scala.concurrent.Future

object CatalogProcessServiceTypes {

  implicit class UpdateEServiceSeedConverter(private val ues: UpdateEServiceSeed) extends AnyVal {
    def toProcess: CatalogProcess.UpdateEServiceSeed = CatalogProcess.UpdateEServiceSeed(
      name = ues.name,
      description = ues.description,
      technology = ues.technology.toProcess
    )
  }

  implicit class EServiceTechnologyConverter(private val est: EServiceTechnology) extends AnyVal {
    def toProcess: CatalogProcess.EServiceTechnology = est match {
      case EServiceTechnology.REST => REST
      case EServiceTechnology.SOAP => SOAP
    }
  }

  implicit class EServiceAttributeValueSeedConverter(private val a: DescriptorAttributeValueSeed) extends AnyVal {
    def toProcess: CatalogProcess.AttributeValueSeed =
      CatalogProcess.AttributeValueSeed(id = a.id, explicitAttributeVerification = a.explicitAttributeVerification)
  }

  implicit class EServiceAttributeSeedConverter(private val e: DescriptorAttributeSeed) extends AnyVal {
    def toProcess: CatalogProcess.AttributeSeed =
      CatalogProcess.AttributeSeed(single = e.single.map(_.toProcess), group = e.group.nested.map(_.toProcess).value)
  }

  implicit class EServiceAttributesSeedConverter(private val esa: DescriptorAttributesSeed) extends AnyVal {
    def toProcess: CatalogProcess.AttributesSeed = CatalogProcess.AttributesSeed(
      certified = esa.certified.map(_.toProcess),
      declared = esa.declared.map(_.toProcess),
      verified = esa.verified.map(_.toProcess)
    )
  }

  implicit class EServiceSeedConverter(private val es: EServiceSeed) extends AnyVal {
    def toProcess: CatalogProcess.EServiceSeed =
      CatalogProcess.EServiceSeed(name = es.name, description = es.description, technology = es.technology.toProcess)
  }

  implicit class EServiceDescriptorSeedConverter(private val seed: EServiceDescriptorSeed) extends AnyVal {
    def toProcess: CatalogProcess.EServiceDescriptorSeed = CatalogProcess.EServiceDescriptorSeed(
      description = seed.description,
      audience = seed.audience,
      voucherLifespan = seed.voucherLifespan,
      dailyCallsPerConsumer = seed.dailyCallsPerConsumer,
      dailyCallsTotal = seed.dailyCallsTotal,
      agreementApprovalPolicy = seed.agreementApprovalPolicy.toProcess,
      attributes = seed.attributes.toProcess
    )
  }

  implicit class EServiceConverter(private val coes: CatalogProcess.EService) extends AnyVal {
    def toApi: CreatedResource                                               = CreatedResource(id = coes.id)
    def toApiWithDescriptorId(descriptorId: UUID): CreatedEServiceDescriptor =
      CreatedEServiceDescriptor(id = coes.id, descriptorId = descriptorId)
  }

  implicit class UpdateEServiceDescriptorDocumentSeedConverter(private val seed: UpdateEServiceDescriptorDocumentSeed)
      extends AnyVal {
    def toProcess: CatalogProcess.UpdateEServiceDescriptorDocumentSeed =
      CatalogProcess.UpdateEServiceDescriptorDocumentSeed(prettyName = seed.prettyName)
  }

  implicit class EServiceDescriptorStateConverter(private val d: CatalogProcess.EServiceDescriptorState)
      extends AnyVal {
    def toApi: EServiceDescriptorState = d match {
      case CatalogProcess.EServiceDescriptorState.DRAFT      => EServiceDescriptorState.DRAFT
      case CatalogProcess.EServiceDescriptorState.PUBLISHED  => EServiceDescriptorState.PUBLISHED
      case CatalogProcess.EServiceDescriptorState.DEPRECATED => EServiceDescriptorState.DEPRECATED
      case CatalogProcess.EServiceDescriptorState.SUSPENDED  => EServiceDescriptorState.SUSPENDED
      case CatalogProcess.EServiceDescriptorState.ARCHIVED   => EServiceDescriptorState.ARCHIVED
    }
  }

  implicit class EServiceAgreementStateConverter(private val d: CatalogProcess.AgreementState) extends AnyVal {
    def toApi: AgreementState = d match {
      case CatalogProcess.AgreementState.ACTIVE                       => AgreementState.ACTIVE
      case CatalogProcess.AgreementState.ARCHIVED                     => AgreementState.ARCHIVED
      case CatalogProcess.AgreementState.DRAFT                        => AgreementState.DRAFT
      case CatalogProcess.AgreementState.MISSING_CERTIFIED_ATTRIBUTES => AgreementState.MISSING_CERTIFIED_ATTRIBUTES
      case CatalogProcess.AgreementState.PENDING                      => AgreementState.PENDING
      case CatalogProcess.AgreementState.REJECTED                     => AgreementState.REJECTED
      case CatalogProcess.AgreementState.SUSPENDED                    => AgreementState.SUSPENDED
    }
  }

  implicit class EServiceDescriptorStateObjectConverter(private val d: CatalogProcess.EServiceDescriptorState.type)
      extends AnyVal {
    def fromApi(s: EServiceDescriptorState): CatalogProcess.EServiceDescriptorState = s match {
      case EServiceDescriptorState.DRAFT      => CatalogProcess.EServiceDescriptorState.DRAFT
      case EServiceDescriptorState.PUBLISHED  => CatalogProcess.EServiceDescriptorState.PUBLISHED
      case EServiceDescriptorState.DEPRECATED => CatalogProcess.EServiceDescriptorState.DEPRECATED
      case EServiceDescriptorState.SUSPENDED  => CatalogProcess.EServiceDescriptorState.SUSPENDED
      case EServiceDescriptorState.ARCHIVED   => CatalogProcess.EServiceDescriptorState.ARCHIVED
    }
  }

  implicit class CpAgreementStateObjectConverter(private val a: CatalogProcess.AgreementState.type) extends AnyVal {
    def fromApi(a: AgreementState): CatalogProcess.AgreementState = a match {
      case AgreementState.DRAFT                        => CatalogProcess.AgreementState.DRAFT
      case AgreementState.ACTIVE                       => CatalogProcess.AgreementState.ACTIVE
      case AgreementState.ARCHIVED                     => CatalogProcess.AgreementState.ARCHIVED
      case AgreementState.PENDING                      => CatalogProcess.AgreementState.PENDING
      case AgreementState.SUSPENDED                    => CatalogProcess.AgreementState.SUSPENDED
      case AgreementState.MISSING_CERTIFIED_ATTRIBUTES => CatalogProcess.AgreementState.MISSING_CERTIFIED_ATTRIBUTES
      case AgreementState.REJECTED                     => CatalogProcess.AgreementState.REJECTED
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

  implicit class AgreementApprovalPolicyConverter(private val aap: AgreementApprovalPolicy) extends AnyVal {
    def toProcess: CatalogProcess.AgreementApprovalPolicy = aap match {
      case AgreementApprovalPolicy.AUTOMATIC => CatalogProcess.AgreementApprovalPolicy.AUTOMATIC
      case AgreementApprovalPolicy.MANUAL    => CatalogProcess.AgreementApprovalPolicy.MANUAL
    }
  }

  implicit class EServiceDocWrapper(private val esd: CatalogProcess.EServiceDoc) extends AnyVal {
    def toApi: EServiceDoc =
      EServiceDoc(id = esd.id, name = esd.name, contentType = esd.contentType, prettyName = esd.prettyName)
  }

  implicit class EServiceDescriptorWrapper(private val esd: CatalogProcess.EServiceDescriptor) extends AnyVal {
    def toCompactDescriptor: CompactDescriptor =
      CompactDescriptor(id = esd.id, state = esd.state.toApi, version = esd.version, audience = esd.audience)
    def toApi: CreatedResource                 = CreatedResource(id = esd.id)
  }

  implicit class EServiceTechnologyWrapper(private val est: CatalogProcess.EServiceTechnology) extends AnyVal {
    def toApi: EServiceTechnology = est match {
      case CatalogProcess.EServiceTechnology.REST => EServiceTechnology.REST
      case CatalogProcess.EServiceTechnology.SOAP => EServiceTechnology.SOAP
    }
  }

  final case class AttributeDetails(name: String, description: String)

  implicit class AttributesWrapper(private val eServiceAttributes: CatalogProcess.Attributes) extends AnyVal {

    def toApi(attributes: Seq[AttributeManagement.Attribute]): Future[DescriptorAttributes] = {
      val attributeNames: Map[UUID, AttributeDetails] =
        attributes.map(attr => attr.id -> AttributeDetails(attr.name, attr.description)).toMap

      for {
        certified <- eServiceAttributes.certified.traverse(convertToApiAttribute(attributeNames))
        declared  <- eServiceAttributes.declared.traverse(convertToApiAttribute(attributeNames))
        verified  <- eServiceAttributes.verified.traverse(convertToApiAttribute(attributeNames))
      } yield DescriptorAttributes(certified = certified, declared = declared, verified = verified)
    }.toFuture

    private def convertToApiAttribute(
      attributeNames: Map[UUID, AttributeDetails]
    )(attribute: CatalogProcess.Attribute): Either[AttributeNotExists, DescriptorAttribute] =
      for {
        single <- attribute.single.traverse(convertToApiAttributeValue(attributeNames))
        group  <- attribute.group.nested.traverse(convertToApiAttributeValue(attributeNames))
      } yield DescriptorAttribute(single = single, group = group.value)

    private def convertToApiAttributeValue(
      attributeNames: Map[UUID, AttributeDetails]
    )(value: CatalogProcess.AttributeValue): Either[AttributeNotExists, DescriptorAttributeValue] =
      attributeNames
        .get(value.id)
        .toRight(AttributeNotExists(value.id))
        .map(attribute =>
          DescriptorAttributeValue(
            id = value.id,
            name = attribute.name,
            description = attribute.description,
            explicitAttributeVerification = value.explicitAttributeVerification
          )
        )
  }

  implicit class UpdateEServiceDescriptorSeedConverter(private val usds: UpdateEServiceDescriptorSeed) extends AnyVal {
    def toProcess: CatalogProcess.UpdateEServiceDescriptorSeed = CatalogProcess.UpdateEServiceDescriptorSeed(
      description = usds.description,
      audience = usds.audience,
      voucherLifespan = usds.voucherLifespan,
      dailyCallsPerConsumer = usds.dailyCallsPerConsumer,
      dailyCallsTotal = usds.dailyCallsTotal,
      agreementApprovalPolicy = usds.agreementApprovalPolicy.toProcess,
      attributes = usds.attributes.toProcess
    )
  }

  implicit class DocumentKindWrapper(private val str: String) extends AnyVal {
    def toProcess: CatalogProcess.EServiceDocumentKind = str match {
      case "DOCUMENT"  => CatalogProcess.EServiceDocumentKind.DOCUMENT
      case "INTERFACE" => CatalogProcess.EServiceDocumentKind.INTERFACE
    }
  }

  final case class EServiceConsumer(
    descriptorVersion: Int,
    descriptorState: EServiceDescriptorState,
    agreementState: AgreementState,
    consumerName: String,
    consumerExternalId: String
  )

  implicit class EServiceConsumerWrapper(private val esc: CatalogProcess.EServiceConsumer) extends AnyVal {
    def toApi: EServiceConsumer =
      EServiceConsumer(
        descriptorVersion = esc.descriptorVersion,
        descriptorState = esc.descriptorState.toApi,
        agreementState = esc.agreementState.toApi,
        consumerName = esc.consumerName,
        consumerExternalId = esc.consumerExternalId
      )
  }
}
