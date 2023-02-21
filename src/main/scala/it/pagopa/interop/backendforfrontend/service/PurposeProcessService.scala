package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.purposeprocess.client.model.{
  DraftPurposeVersionUpdateContent,
  PurposeVersion,
  PurposeVersionState,
  Purposes
}

import java.util.UUID
import scala.concurrent.Future

trait PurposeProcessService {

  def getPurposes(
    name: Option[String],
    eServicesIds: Seq[UUID],
    consumersIds: Seq[UUID],
    producersIds: Seq[UUID],
    states: Seq[PurposeVersionState],
    offset: Int,
    limit: Int
  )(implicit contexts: Seq[(String, String)]): Future[Purposes]

  def updateDraftPurposeVersion(purposeId: UUID, versionId: UUID, updateContent: DraftPurposeVersionUpdateContent)(
    implicit contexts: Seq[(String, String)]
  ): Future[PurposeVersion]
}
