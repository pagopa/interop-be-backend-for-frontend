package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.purposeprocess.client.{model => PurposeProcess}

object PurposeProcessServiceTypes {

  type DraftPurposeVersionProcess    = PurposeProcess.DraftPurposeVersionUpdateContent
  type PurposeVersionProcess         = PurposeProcess.PurposeVersion
  type PurposeVersionDocumentProcess = PurposeProcess.PurposeVersionDocument

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

  implicit class PurposeVersionDocumentConverter(private val pvdc: PurposeVersionDocumentProcess) extends AnyVal {
    def toApi: PurposeVersionDocument =
      PurposeVersionDocument(id = pvdc.id, contentType = pvdc.contentType, createdAt = pvdc.createdAt)
  }

  implicit class PurposeVersionConverter(private val pvc: PurposeVersionProcess) extends AnyVal {
    def toApi: PurposeVersion = PurposeVersion(
      id = pvc.id,
      state = pvc.state.toApi,
      createdAt = pvc.createdAt,
      updatedAt = pvc.updatedAt,
      firstActivationAt = pvc.firstActivationAt,
      expectedApprovalDate = pvc.expectedApprovalDate,
      dailyCalls = pvc.dailyCalls,
      riskAnalysis = pvc.riskAnalysis.map(_.toApi)
    )
  }
}
