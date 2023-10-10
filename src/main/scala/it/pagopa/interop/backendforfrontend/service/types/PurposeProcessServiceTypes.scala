package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.agreementprocess.client.{model => AgreementProcess}
import it.pagopa.interop.authorizationprocess.client.{model => AuthorizationProcess}
import it.pagopa.interop.backendforfrontend.api.impl.Utils.isUpgradable
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service.types.AgreementProcessServiceTypes.AgreementStateConverter
import it.pagopa.interop.backendforfrontend.service.types.AuthorizationProcessServiceTypes.ClientWithKeysConverter
import it.pagopa.interop.backendforfrontend.service.types.CatalogProcessServiceTypes.EServiceDescriptorStateConverter
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}
import it.pagopa.interop.purposeprocess.client.{model => PurposeProcess}
import it.pagopa.interop.tenantprocess.client.{model => TenantProcess}

object PurposeProcessServiceTypes {

  implicit class PurposeVersionStateConverter(private val v: PurposeProcess.PurposeVersionState) extends AnyVal {
    def toApi: PurposeVersionState = v match {
      case PurposeProcess.PurposeVersionState.ACTIVE               => PurposeVersionState.ACTIVE
      case PurposeProcess.PurposeVersionState.DRAFT                => PurposeVersionState.DRAFT
      case PurposeProcess.PurposeVersionState.SUSPENDED            => PurposeVersionState.SUSPENDED
      case PurposeProcess.PurposeVersionState.WAITING_FOR_APPROVAL => PurposeVersionState.WAITING_FOR_APPROVAL
      case PurposeProcess.PurposeVersionState.ARCHIVED             => PurposeVersionState.ARCHIVED
    }
  }

  implicit class PurposeVersionSeedConverter(private val seed: PurposeVersionSeed) extends AnyVal {
    def toProcess: PurposeProcess.PurposeVersionSeed = PurposeProcess.PurposeVersionSeed(dailyCalls = seed.dailyCalls)
  }

  implicit class PurposeSeedConverter(private val seed: PurposeSeed) extends AnyVal {
    def toProcess: PurposeProcess.PurposeSeed = PurposeProcess.PurposeSeed(
      eserviceId = seed.eserviceId,
      consumerId = seed.consumerId,
      riskAnalysisForm = seed.riskAnalysisForm.map(_.toProcess),
      title = seed.title,
      description = seed.description,
      isFreeOfCharge = seed.isFreeOfCharge,
      freeOfChargeReason = seed.freeOfChargeReason,
      dailyCalls = seed.dailyCalls
    )
  }

  implicit class ProcessRiskAnalysisFormConverter(private val raf: PurposeProcess.RiskAnalysisForm) extends AnyVal {
    def toApi: RiskAnalysisForm =
      RiskAnalysisForm(version = raf.version, answers = raf.answers, riskAnalysisId = raf.riskAnalysisId)
  }

  implicit class RiskAnalysisFormSeedConverter(private val raf: RiskAnalysisFormSeed) extends AnyVal {
    def toProcess: PurposeProcess.RiskAnalysisFormSeed =
      PurposeProcess.RiskAnalysisFormSeed(version = raf.version, answers = raf.answers)
  }

  implicit class PurposeVersionUpdateSeedConverter(private val seed: WaitingForApprovalPurposeVersionUpdateContentSeed)
      extends AnyVal {
    def toSeed: PurposeProcess.WaitingForApprovalPurposeVersionUpdateContent =
      PurposeProcess.WaitingForApprovalPurposeVersionUpdateContent(expectedApprovalDate = seed.expectedApprovalDate)
  }

  implicit class PurposeVersionsConverter(private val pv: PurposeProcess.PurposeVersion) extends AnyVal {
    def toApi: PurposeVersion = PurposeVersion(
      id = pv.id,
      state = pv.state.toApi,
      createdAt = pv.createdAt,
      expectedApprovalDate = pv.expectedApprovalDate,
      updatedAt = pv.updatedAt,
      firstActivationAt = pv.firstActivationAt,
      dailyCalls = pv.dailyCalls,
      riskAnalysisDocument = pv.riskAnalysis.map(_.toApi),
      suspendedAt = pv.suspendedAt
    )
  }

  implicit class PurposeEServiceSeedConverter(private val seed: PurposeEServiceSeed) extends AnyVal {
    def toProcess: PurposeProcess.EServicePurposeSeed =
      PurposeProcess.EServicePurposeSeed(
        eServiceId = seed.eserviceId,
        consumerId = seed.consumerId,
        riskAnalysisId = seed.riskAnalysisId,
        title = seed.title,
        description = seed.description,
        isFreeOfCharge = seed.isFreeOfCharge,
        freeOfChargeReason = seed.freeOfChargeReason,
        dailyCalls = seed.dailyCalls
      )
  }

  implicit class ClientConverter(private val c: AuthorizationProcess.Client) extends AnyVal {
    def toApi(hasKeys: Boolean): CompactClient = CompactClient(id = c.id, name = c.name, hasKeys = hasKeys)
  }

  implicit class RiskAnalysisDocumentConverter(private val rad: PurposeProcess.PurposeVersionDocument) extends AnyVal {
    def toApi: PurposeVersionDocument =
      PurposeVersionDocument(id = rad.id, contentType = rad.contentType, createdAt = rad.createdAt)
  }

  implicit class PurposeConverter(private val p: PurposeProcess.Purpose) extends AnyVal {
    def toApi(
      eService: CatalogProcess.EService,
      agreement: AgreementProcess.Agreement,
      currentDescriptor: CatalogProcess.EServiceDescriptor,
      currentVersion: Option[PurposeProcess.PurposeVersion],
      producer: TenantProcess.Tenant,
      consumer: TenantProcess.Tenant,
      clients: Seq[AuthorizationProcess.ClientWithKeys],
      waitingForApprovalVersion: Option[PurposeProcess.PurposeVersion]
    ): Purpose = Purpose(
      id = p.id,
      title = p.title,
      description = p.description,
      consumer = CompactOrganization(id = consumer.id, name = consumer.name),
      riskAnalysisForm = p.riskAnalysisForm.map(_.toApi),
      eservice = CompactPurposeEService(
        id = eService.id,
        name = eService.name,
        producer = CompactOrganization(id = producer.id, name = producer.name),
        descriptor = CompactDescriptor(
          id = currentDescriptor.id,
          state = currentDescriptor.state.toApi,
          version = currentDescriptor.version,
          audience = currentDescriptor.audience
        )
      ),
      agreement = {
        val canBeUpgraded: Boolean = eService.descriptors
          .find(_.id == agreement.descriptorId)
          .exists(isUpgradable(_, eService.descriptors))
        CompactAgreement(id = agreement.id, state = agreement.state.toApi, canBeUpgraded = canBeUpgraded)
      },
      currentVersion = currentVersion.map(_.toApi),
      versions = p.versions.map(_.toApi),
      clients = clients.map(_.toApi),
      waitingForApprovalVersion = waitingForApprovalVersion.map(_.toApi),
      suspendedByConsumer = p.suspendedByConsumer,
      suspendedByProducer = p.suspendedByProducer,
      isFreeOfCharge = p.isFreeOfCharge,
      freeOfChargeReason = p.freeOfChargeReason,
      dailyCallsPerConsumer = currentDescriptor.dailyCallsPerConsumer,
      dailyCallsTotal = currentDescriptor.dailyCallsTotal
    )

    def toApiResource: CreatedResource = CreatedResource(id = p.id)
  }

  implicit class PurposeUpdateContentConverter(private val puc: PurposeUpdateContent) extends AnyVal {
    def toProcess: PurposeProcess.PurposeUpdateContent =
      PurposeProcess.PurposeUpdateContent(
        title = puc.title,
        description = puc.description,
        isFreeOfCharge = puc.isFreeOfCharge,
        freeOfChargeReason = puc.freeOfChargeReason,
        riskAnalysisForm = puc.riskAnalysisForm.map(_.toProcess),
        dailyCalls = puc.dailyCalls
      )
  }

  implicit class ReversePurposeUpdateContentConverter(private val puc: ReversePurposeUpdateContent) extends AnyVal {
    def toProcess: PurposeProcess.ReversePurposeUpdateContent =
      PurposeProcess.ReversePurposeUpdateContent(
        title = puc.title,
        description = puc.description,
        isFreeOfCharge = puc.isFreeOfCharge,
        freeOfChargeReason = puc.freeOfChargeReason,
        dailyCalls = puc.dailyCalls
      )
  }

  implicit class RiskAnalysisFormConfigWrapper(
    private val riskAnalysisFormConfig: PurposeProcess.RiskAnalysisFormConfigResponse
  ) extends AnyVal {
    def toApi: RiskAnalysisFormConfig =
      RiskAnalysisFormConfig(
        version = riskAnalysisFormConfig.version,
        questions = riskAnalysisFormConfig.questions.map(_.toApi)
      )
  }

  implicit class FormConfigQuestionWrapper(private val question: PurposeProcess.FormConfigQuestionResponse)
      extends AnyVal {
    def toApi: FormConfigQuestion =
      FormConfigQuestion(
        id = question.id,
        label = question.label.toApi,
        infoLabel = question.infoLabel.map(_.toApi),
        dataType = question.dataType.toApi,
        required = question.required,
        dependencies = question.dependencies.map(_.toApi),
        visualType = question.visualType,
        defaultValue = question.defaultValue,
        hideOption = question.hideOption.map(_.toApi),
        validation = question.validation.map(_.toApi),
        options = question.options.map(_.map(_.toApi))
      )
  }

  implicit class ValidationWrapper(private val validation: PurposeProcess.ValidationOptionResponse) extends AnyVal {
    def toApi: ValidationOption =
      ValidationOption(maxLength = validation.maxLength)
  }

  implicit class MapHideOptionWrapper(private val mapHideOption: Map[String, Seq[PurposeProcess.HideOptionResponse]])
      extends AnyVal {
    def toApi: Map[String, Seq[HideOption]] = mapHideOption.map { case (k, v) => (k, v.map(_.toApi)) }
  }

  implicit class HideOptionWrapper(private val hideOption: PurposeProcess.HideOptionResponse) extends AnyVal {
    def toApi: HideOption =
      HideOption(id = hideOption.id, value = hideOption.value)
  }

  implicit class LocalizedTextWrapper(private val localizedText: PurposeProcess.LocalizedTextResponse) extends AnyVal {
    def toApi: LocalizedText =
      LocalizedText(it = localizedText.it, en = localizedText.en)
  }

  implicit class LabeledValueWrapper(private val labeledValue: PurposeProcess.LabeledValueResponse) extends AnyVal {
    def toApi: LabeledValue =
      LabeledValue(label = labeledValue.label.toApi, value = labeledValue.value)
  }

  implicit class DataTypeWrapper(private val dataType: PurposeProcess.DataTypeResponse) extends AnyVal {
    def toApi: DataType =
      dataType match {
        case PurposeProcess.DataTypeResponse.SINGLE   => DataType.SINGLE
        case PurposeProcess.DataTypeResponse.MULTI    => DataType.MULTI
        case PurposeProcess.DataTypeResponse.FREETEXT => DataType.FREETEXT
      }
  }

  implicit class DependencyWrapper(private val dependency: PurposeProcess.DependencyResponse) extends AnyVal {
    def toApi: Dependency =
      Dependency(id = dependency.id, value = dependency.value)
  }
}
