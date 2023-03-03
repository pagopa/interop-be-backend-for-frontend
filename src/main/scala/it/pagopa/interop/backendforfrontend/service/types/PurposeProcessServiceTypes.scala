package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.purposeprocess.client.{model => PurposeProcess}
import it.pagopa.interop.agreementprocess.client.{model => AgreementProcess}
import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagement}
import it.pagopa.interop.backendforfrontend.api.impl.Utils.isUpgradable
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}
import it.pagopa.interop.tenantprocess.client.{model => TenantProcess}
import it.pagopa.interop.backendforfrontend.model.AgreementState._

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
      updatedAt = pv.updatedAt,
      firstActivationAt = pv.firstActivationAt,
      expectedApprovalDate = pv.expectedApprovalDate,
      dailyCalls = pv.dailyCalls,
      riskAnalysisDocument = pv.riskAnalysis.map(_.toApi)
    )
  }

  implicit class ClientConverter(private val c: AuthorizationManagement.Client) extends AnyVal {
    def toApi: Client = Client(id = c.id, name = c.name, hasKeys = ???) // TODO
  }

  implicit class RiskAnalysisDocumentConverter(private val rad: PurposeProcess.PurposeVersionDocument) extends AnyVal {
    def toApi: PurposeVersionDocument =
      PurposeVersionDocument(id = rad.id, contentType = rad.contentType, createdAt = rad.createdAt)
  }

  implicit class RiskAnalysisFormConverter(private val raf: Option[PurposeProcess.RiskAnalysisForm]) extends AnyVal {
    def toApi: RiskAnalysisForm = raf match {
      case Some(value) => RiskAnalysisForm(version = value.version, answers = value.answers)
      case None        => RiskAnalysisForm(version = "v1", answers = Map.empty)
    }
  }

  implicit class AgreementStateConverter(private val s: AgreementProcess.AgreementState) extends AnyVal {
    def toApi: AgreementState = s match {
      case AgreementProcess.AgreementState.DRAFT                        => DRAFT
      case AgreementProcess.AgreementState.ACTIVE                       => ACTIVE
      case AgreementProcess.AgreementState.ARCHIVED                     => ARCHIVED
      case AgreementProcess.AgreementState.PENDING                      => PENDING
      case AgreementProcess.AgreementState.SUSPENDED                    => SUSPENDED
      case AgreementProcess.AgreementState.MISSING_CERTIFIED_ATTRIBUTES => MISSING_CERTIFIED_ATTRIBUTES
      case AgreementProcess.AgreementState.REJECTED                     => REJECTED
    }
  }

  implicit class PurposeConverter(private val p: PurposeProcess.Purpose) extends AnyVal {
    def toApi(
      purpose: PurposeProcess.Purpose,
      eService: CatalogProcess.EService,
      agreement: Option[AgreementProcess.Agreement],
      currentVersion: Option[PurposeProcess.PurposeVersion],
      producer: TenantProcess.Tenant,
      consumer: TenantProcess.Tenant,
      clients: Seq[AuthorizationManagement.Client],
      waitingForApprovalVersion: Option[PurposeProcess.PurposeVersion]
    ): Purpose = Purpose(
      id = purpose.id,
      title = purpose.title,
      description = purpose.description,
      consumer = CompactOrganization(id = consumer.id, name = consumer.name),
      riskAnalysisForm = purpose.riskAnalysisForm.toApi,
      eservice = CompactEService(
        id = eService.id,
        name = eService.name,
        producer = CompactOrganization(id = producer.id, name = producer.name)
      ),
      agreement = {
        val a                      = agreement.get // Agreement can't be none. A purpose has always an agreement
        val canBeUpgraded: Boolean = eService.descriptors
          .find(_.id == a.descriptorId)
          .exists(isUpgradable(_, eService.descriptors))
        CompactAgreement(id = a.id, state = a.state.toApi, canBeUpgraded = canBeUpgraded)
      },
      currentVersion =
        currentVersion.map(v => CompactPurposeVersion(id = v.id, state = v.state.toApi, dailyCalls = v.dailyCalls)),
      versions = purpose.versions.map(_.toApi),
      clients = clients.map(_.toApi),
      waitingForApprovalVersion = waitingForApprovalVersion.map(v =>
        CompactPurposeVersion(id = v.id, state = v.state.toApi, dailyCalls = v.dailyCalls, v.expectedApprovalDate)
      ),
      suspendedByConsumer = purpose.suspendedByConsumer,
      suspendedByProducer = purpose.suspendedByProducer
    )
  }
}
