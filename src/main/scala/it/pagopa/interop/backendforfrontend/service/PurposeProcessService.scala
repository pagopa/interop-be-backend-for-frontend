package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.purposeprocess.client.model.{PurposeVersionState, Purposes}

import java.util.UUID
import java.io.File
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

  def getRiskAnalysisDocument(purposeId: UUID, versionId: UUID, documentId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[File]

}
