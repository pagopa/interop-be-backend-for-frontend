package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.purposeprocess.client.{model => PurposeProcess}

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

  implicit class RiskAnalysisFormConverter(private val raf: RiskAnalysisForm) extends AnyVal {
    def toProcess: PurposeProcess.RiskAnalysisForm =
      PurposeProcess.RiskAnalysisForm(version = raf.version, answers = raf.answers)
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
