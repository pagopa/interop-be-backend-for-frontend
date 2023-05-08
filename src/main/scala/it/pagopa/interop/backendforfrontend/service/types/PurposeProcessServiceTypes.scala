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

  type DraftPurposeVersionProcess = PurposeProcess.DraftPurposeVersionUpdateContent

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
      description = seed.description
    )
  }

  implicit class ProcessRiskAnalysisFormConverter(private val raf: PurposeProcess.RiskAnalysisForm) extends AnyVal {
    def toApi: RiskAnalysisForm = RiskAnalysisForm(version = raf.version, answers = raf.answers)
  }

  implicit class RiskAnalysisFormConverter(private val raf: RiskAnalysisForm) extends AnyVal {
    def toProcess: PurposeProcess.RiskAnalysisForm =
      PurposeProcess.RiskAnalysisForm(version = raf.version, answers = raf.answers)
  }

  implicit class PurposeVersionUpdateSeedConverter(private val seed: WaitingForApprovalPurposeVersionUpdateContentSeed)
      extends AnyVal {
    def toSeed: PurposeProcess.WaitingForApprovalPurposeVersionUpdateContent =
      PurposeProcess.WaitingForApprovalPurposeVersionUpdateContent(expectedApprovalDate = seed.expectedApprovalDate)
  }

  implicit class DraftPurposeVersionUpdateContentConverter(private val dpvc: DraftPurposeVersionUpdateContent)
      extends AnyVal {
    def toProcess: DraftPurposeVersionProcess =
      PurposeProcess.DraftPurposeVersionUpdateContent(dailyCalls = dpvc.dailyCalls)
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
      suspendedByProducer = p.suspendedByProducer
    )

    def toApiResource: CreatedResource = CreatedResource(id = p.id)
  }

  implicit class PurposeUpdateContentConverter(private val puc: PurposeUpdateContent) extends AnyVal {
    def toProcess: PurposeProcess.PurposeUpdateContent =
      PurposeProcess.PurposeUpdateContent(
        title = puc.title,
        description = puc.description,
        riskAnalysisForm = puc.riskAnalysisForm.map(_.toProcess)
      )
  }
}
