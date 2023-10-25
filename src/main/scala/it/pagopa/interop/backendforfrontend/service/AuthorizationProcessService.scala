package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.authorizationprocess.client.model._

import java.util.UUID
import scala.concurrent.Future

trait AuthorizationProcessService {
  def getClient(clientId: UUID)(implicit contexts: Seq[(String, String)]): Future[Client]

  def deleteClient(clientId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def removeClientPurpose(clientId: UUID, purposeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def deleteClientKeyById(clientId: UUID, keyId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def removeUser(clientId: UUID, userId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def addUser(clientId: UUID, userId: UUID)(implicit contexts: Seq[(String, String)]): Future[Client]

  def addClientPurpose(clientId: UUID, purposeAdditionDetails: PurposeAdditionDetails)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]

  def getClientKeys(clientId: UUID, userIds: Seq[UUID])(implicit contexts: Seq[(String, String)]): Future[Keys]

  def getClientKeyById(clientId: UUID, keyId: String)(implicit contexts: Seq[(String, String)]): Future[Key]

  def getClientUsers(clientId: UUID)(implicit contexts: Seq[(String, String)]): Future[Seq[UUID]]

  def createKeys(clientId: UUID, keysSeed: Seq[KeySeed])(implicit contexts: Seq[(String, String)]): Future[Keys]

  def createConsumerClient(clientSeed: ClientSeed)(implicit contexts: Seq[(String, String)]): Future[Client]

  def createApiClient(clientSeed: ClientSeed)(implicit contexts: Seq[(String, String)]): Future[Client]

  def getClients(
    name: Option[String],
    userIds: Seq[UUID],
    consumerId: UUID,
    purposeId: Option[UUID],
    kind: Option[ClientKind],
    limit: Int,
    offset: Int
  )(implicit contexts: Seq[(String, String)]): Future[Clients]

  def getClientsWithKeys(
    name: Option[String],
    userIds: Seq[UUID],
    consumerId: UUID,
    purposeId: Option[UUID],
    kind: Option[ClientKind],
    limit: Int,
    offset: Int
  )(implicit contexts: Seq[(String, String)]): Future[ClientsWithKeys]

  def removePurposeFromClients(purposeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]
}
