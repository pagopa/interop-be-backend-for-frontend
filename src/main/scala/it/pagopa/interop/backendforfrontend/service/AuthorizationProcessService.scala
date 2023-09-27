package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.authorizationprocess.client.model._

import java.util.UUID
import scala.concurrent.Future

trait AuthorizationProcessService {
  def getClient(clientId: UUID)(implicit contexts: Seq[(String, String)]): Future[Client]

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

  def getClientKeys(clientId: UUID, relationshipIds: Seq[UUID])(implicit contexts: Seq[(String, String)]): Future[Keys]

  def getClientKeyById(clientId: UUID, keyId: String)(implicit contexts: Seq[(String, String)]): Future[Key]

  def getClientOperators(clientId: UUID)(implicit contexts: Seq[(String, String)]): Future[Seq[Operator]]

  def createKeys(clientId: UUID, keysSeed: Seq[KeySeed])(implicit contexts: Seq[(String, String)]): Future[Keys]

  def createConsumerClient(clientSeed: ClientSeed)(implicit contexts: Seq[(String, String)]): Future[Client]

  def createApiClient(clientSeed: ClientSeed)(implicit contexts: Seq[(String, String)]): Future[Client]

  def getClients(
    name: Option[String],
    relationshipIds: Seq[UUID],
    consumerId: UUID,
    purposeId: Option[UUID],
    kind: Option[ClientKind],
    limit: Int,
    offset: Int
  )(implicit contexts: Seq[(String, String)]): Future[Clients]

  def getClientsWithKeys(
    name: Option[String],
    relationshipIds: Seq[UUID],
    consumerId: UUID,
    purposeId: Option[UUID],
    kind: Option[ClientKind],
    limit: Int,
    offset: Int
  )(implicit contexts: Seq[(String, String)]): Future[ClientsWithKeys]

  def removePurposeFromClients(purposeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]
}
