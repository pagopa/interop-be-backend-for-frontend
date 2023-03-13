package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.authorizationprocess.client.model._

import java.util.UUID
import scala.concurrent.Future

trait AuthorizationProcessService {

  def deleteClient(clientId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def removeClientPurpose(clientId: UUID, purposeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def deleteClientKeyById(clientId: UUID, keyId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def removeClientOperatorRelationship(clientId: UUID, relationshipId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]

  def clientOperatorRelationshipBinding(clientId: UUID, relationshipId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Client]

  def addClientPurpose(clientId: UUID, purposeAdditionDetails: PurposeAdditionDetails)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]

  def getClients(consumerId: UUID, purposeId: Option[UUID])(implicit contexts: Seq[(String, String)]): Future[Clients]

  def getClientKeys(clientId: UUID)(implicit contexts: Seq[(String, String)]): Future[ReadClientKeys]

  def getClientOperatorKeys(clientId: UUID, operatorId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[ClientKeys]
}
