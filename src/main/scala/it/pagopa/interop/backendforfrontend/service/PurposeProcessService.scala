package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.purposeprocess.client.model.{PurposeVersionState, Purposes}

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

  def deletePurposeVersion(purposeId: UUID, versionId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]

}
