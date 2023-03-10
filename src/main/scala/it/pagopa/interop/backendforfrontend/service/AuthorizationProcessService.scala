package it.pagopa.interop.backendforfrontend.service

import java.util.UUID
import scala.concurrent.Future
import it.pagopa.interop.authorizationprocess.client.model.{Clients, ReadClientKeys}

trait AuthorizationProcessService {

  def deleteClient(clientId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def removeClientPurpose(clientId: UUID, purposeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def deleteClientKeyById(clientId: UUID, keyId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]
  def removeClientOperatorRelationship(clientId: UUID, relationshipId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]

  def getClients(consumerId: UUID, purposeId: Option[UUID])(implicit contexts: Seq[(String, String)]): Future[Clients]

  def getClientKeys(clientId: UUID)(implicit contexts: Seq[(String, String)]): Future[ReadClientKeys]
}
