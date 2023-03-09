package it.pagopa.interop.backendforfrontend.service

import java.util.UUID
import scala.concurrent.Future
import it.pagopa.interop.authorizationprocess.client.model.Clients

trait AuthorizationProcessService {

  def deleteClient(clientId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def removeClientPurpose(clientId: UUID, purposeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def deleteClientKeyById(clientId: UUID, keyId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def removeClientOperatorRelationship(clientId: UUID, relationshipId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]

  def getClients(name: Option[String] = None, relationshipIds: Seq[UUID] = Seq.empty, limit: Int, offset: Int = 0)(
    implicit contexts: Seq[(String, String)]
  ): Future[Clients]
}
