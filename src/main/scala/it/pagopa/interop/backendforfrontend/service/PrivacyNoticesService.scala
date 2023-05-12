package it.pagopa.interop.backendforfrontend.service

import java.util.UUID
import scala.concurrent.Future
import it.pagopa.interop.backendforfrontend.service.model._

trait PrivacyNoticesService {
  def getLatestVersion(id: UUID)(implicit contexts: Seq[(String, String)]): Future[Option[PrivacyNotice]]

  def getByUserId(id: UUID, userId: UUID)(implicit contexts: Seq[(String, String)]): Future[Option[UserPrivacyNotice]]
  def put(userPrivacyNotice: UserPrivacyNotice)(implicit contexts: Seq[(String, String)]): Future[Unit]
}
