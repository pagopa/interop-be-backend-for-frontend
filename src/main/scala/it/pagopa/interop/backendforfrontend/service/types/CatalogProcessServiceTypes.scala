package it.pagopa.interop.backendforfrontend.service.types

import cats.syntax.all._
import it.pagopa.interop.attributeregistryprocess.client.{model => AttributeProcess}
import it.pagopa.interop.backendforfrontend.error.BFFErrors.AttributeNotExists
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.catalogmanagement.{model => CatalogManagement}
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}

import java.util.UUID
import scala.concurrent.Future

object CatalogProcessServiceTypes {

  implicit class UpdateEServiceSeedConverter(private val ues: UpdateEServiceSeed) extends AnyVal {
    def toProcess: CatalogProcess.UpdateEServiceSeed = CatalogProcess.UpdateEServiceSeed(
      name = ues.name,
      description = ues.description,
      technology = ues.technology.toProcess,
      mode = ues.mode.toProcess
    )
  }

  implicit class EServiceModeConverter(private val esm: EServiceMode) extends AnyVal {
    def toProcess: CatalogProcess.EServiceMode = esm match {
      case EServiceMode.DELIVER => CatalogProcess.EServiceMode.DELIVER
      case EServiceMode.RECEIVE => CatalogProcess.EServiceMode.RECEIVE
    }
  }

  implicit class CatalogEServiceModeConverter(private val esm: CatalogProcess.EServiceMode) extends AnyVal {
    def toApi: EServiceMode = esm match {
      case CatalogProcess.EServiceMode.DELIVER => EServiceMode.DELIVER
      case CatalogProcess.EServiceMode.RECEIVE => EServiceMode.RECEIVE
    }
  }

  implicit class CatalogEServiceRiskAnalysisConverter(private val era: CatalogProcess.EServiceRiskAnalysis)
      extends AnyVal {
    def toApi: EServiceRiskAnalysis = EServiceRiskAnalysis(
      id = era.id,
      name = era.name,
      riskAnalysisForm = era.riskAnalysisForm.toApi,
      createdAt = era.createdAt
    )
  }

  implicit class CatalogEServiceRiskAnalysisFormConverter(private val eraf: CatalogProcess.EServiceRiskAnalysisForm)
      extends AnyVal {
    def toApi: RiskAnalysisForm = RiskAnalysisForm(
      version = eraf.version,
      answers =
        (eraf.singleAnswers.map(a => a.key -> a.value.toSeq) ++ eraf.multiAnswers.map(a => a.key -> a.values)).toMap
    )
  }

  implicit class RiskAnalysisFormConverter(private val ra: RiskAnalysisForm) extends AnyVal {
    def toProcess: CatalogProcess.EServiceRiskAnalysisFormSeed =
      CatalogProcess.EServiceRiskAnalysisFormSeed(version = ra.version, answers = ra.answers)
  }

  implicit class EServiceTechnologyConverter(private val est: EServiceTechnology) extends AnyVal {
    def toProcess: CatalogProcess.EServiceTechnology = est match {
      case EServiceTechnology.REST => CatalogProcess.EServiceTechnology.REST
      case EServiceTechnology.SOAP => CatalogProcess.EServiceTechnology.SOAP
    }
  }

  implicit class EServiceAttributeSeedConverter(private val a: DescriptorAttributeSeed) extends AnyVal {
    def toProcess: CatalogProcess.AttributeSeed =
      CatalogProcess.AttributeSeed(id = a.id, explicitAttributeVerification = a.explicitAttributeVerification)
  }

  implicit class EServiceAttributesSeedConverter(private val esa: DescriptorAttributesSeed) extends AnyVal {
    def toProcess: CatalogProcess.AttributesSeed = CatalogProcess.AttributesSeed(
      certified = esa.certified.map(_.map(_.toProcess)),
      declared = esa.declared.map(_.map(_.toProcess)),
      verified = esa.verified.map(_.map(_.toProcess))
    )
  }

  implicit class EServiceSeedConverter(private val es: EServiceSeed) extends AnyVal {
    def toProcess: CatalogProcess.EServiceSeed =
      CatalogProcess.EServiceSeed(
        name = es.name,
        description = es.description,
        technology = es.technology.toProcess,
        mode = es.mode.toProcess
      )
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

  implicit class AttributeValueWrapper(private val av: CatalogProcess.Attribute) extends AnyVal {
    def toPersistent: CatalogManagement.CatalogAttribute =
      CatalogManagement.CatalogAttribute(av.id, av.explicitAttributeVerification)
  }

  implicit class AttributesWrapper(private val eServiceAttributes: CatalogProcess.Attributes) extends AnyVal {

    def toPersistent: CatalogManagement.CatalogAttributes = CatalogManagement.CatalogAttributes(
      certified = eServiceAttributes.certified.map(_.map(_.toPersistent)),
      declared = eServiceAttributes.declared.map(_.map(_.toPersistent)),
      verified = eServiceAttributes.verified.map(_.map(_.toPersistent))
    )

    def toApi(attributes: Seq[AttributeProcess.Attribute]): Future[DescriptorAttributes] = {
      val attributeNames: Map[UUID, AttributeDetails] =
        attributes.map(attr => attr.id -> AttributeDetails(attr.name, attr.description)).toMap

      for {
        certified <- eServiceAttributes.certified.traverse(_.traverse(convertToApiAttribute(attributeNames)))
        declared  <- eServiceAttributes.declared.traverse(_.traverse(convertToApiAttribute(attributeNames)))
        verified  <- eServiceAttributes.verified.traverse(_.traverse(convertToApiAttribute(attributeNames)))
      } yield DescriptorAttributes(certified = certified, declared = declared, verified = verified)
    }.toFuture

    private def convertToApiAttribute(
      attributeNames: Map[UUID, AttributeDetails]
    )(value: CatalogProcess.Attribute): Either[AttributeNotExists, DescriptorAttribute] =
      attributeNames
        .get(value.id)
        .toRight(AttributeNotExists(value.id))
        .map(attribute =>
          DescriptorAttribute(
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

  implicit class EServiceRiskAnalysisSeedConverter(private val eras: EServiceRiskAnalysisSeed) extends AnyVal {
    def toProcess: CatalogProcess.EServiceRiskAnalysisSeed =
      CatalogProcess.EServiceRiskAnalysisSeed(name = eras.name, riskAnalysisForm = eras.riskAnalysisForm.toProcess)
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
