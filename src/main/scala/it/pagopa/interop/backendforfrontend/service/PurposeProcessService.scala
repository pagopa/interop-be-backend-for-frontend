package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.purposeprocess.client.model._

import java.util.UUID
import scala.concurrent.Future

trait PurposeProcessService {

  def getPurposes(
    name: Option[String],
    eServicesIds: Seq[UUID],
    consumersIds: Seq[UUID],
    producersIds: Seq[UUID],
    states: Seq[PurposeVersionState],
    excludeDraft: Option[Boolean],
    offset: Int,
    limit: Int
  )(implicit contexts: Seq[(String, String)]): Future[Purposes]

  def createPurposeVersion(purposeId: UUID, seed: PurposeVersionSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[PurposeVersion]

  def clonePurpose(purposeUuid: UUID)(implicit contexts: Seq[(String, String)]): Future[Purpose]

  def deletePurposeVersion(purposeId: UUID, versionId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def archivePurposeVersion(purposeId: UUID, versionId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[PurposeVersion]

  def suspendPurposeVersion(purposeId: UUID, versionId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[PurposeVersion]

  def updateWaitingForApprovalPurposeVersion(
    purposeId: UUID,
    versionId: UUID,
    waitingForApprovalPurposeVersionUpdateContent: WaitingForApprovalPurposeVersionUpdateContent
  )(implicit contexts: Seq[(String, String)]): Future[PurposeVersion]

  def deletePurpose(purposeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def getRiskAnalysisDocument(purposeId: UUID, versionId: UUID, documentId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[PurposeVersionDocument]

  def activatePurposeVersion(purposeId: UUID, versionId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[PurposeVersion]

  def createPurpose(seed: PurposeSeed)(implicit contexts: Seq[(String, String)]): Future[Purpose]

  def getPurpose(id: UUID)(implicit contexts: Seq[(String, String)]): Future[Purpose]

  def updatePurpose(id: UUID, purposeUpdateContent: PurposeUpdateContent)(implicit
    contexts: Seq[(String, String)]
  ): Future[Purpose]

  def updateReversePurpose(id: UUID, reversePurposeUpdateContent: ReversePurposeUpdateContent)(implicit
    contexts: Seq[(String, String)]
  ): Future[Purpose]

  def retrieveLatestRiskAnalysisConfiguration()(implicit
    contexts: Seq[(String, String)]
  ): Future[RiskAnalysisFormConfigResponse]

  def retrieveRiskAnalysisConfigurationByVersion(riskAnalysisVersion: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[RiskAnalysisFormConfigResponse]

  def createPurposeFromEService(eServicePurposeSeed: EServicePurposeSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Purpose]

}
