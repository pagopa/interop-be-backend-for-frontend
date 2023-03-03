package it.pagopa.interop.backendforfrontend.service
import it.pagopa.interop.authorizationmanagement.client.model.{Client, ClientKind}

import java.util.UUID
import scala.concurrent.Future

trait AuthorizationManagementService {
  def getClients(purposeId: Option[UUID])(implicit contexts: Seq[(String, String)]): Future[Seq[Client]]
}
