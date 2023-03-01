package it.pagopa.interop.backendforfrontend.service

import java.util.UUID
import scala.concurrent.Future

trait AuthorizationProcessService {

  def deleteClient(clientId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def removeClientPurpose(clientId: UUID, purposeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def deleteClientKeyById(clientId: UUID, keyId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]
}
