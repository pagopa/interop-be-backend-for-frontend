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

  def getClientKeys(clientId: UUID)(implicit contexts: Seq[(String, String)]): Future[ReadClientKeys]

  def getClientOperators(clientId: UUID)(implicit contexts: Seq[(String, String)]): Future[Seq[Operator]]

  def createKeys(clientId: UUID, keysSeed: Seq[KeySeed])(implicit contexts: Seq[(String, String)]): Future[ClientKeys]

  def getEncodedClientKeyById(clientId: UUID, keyId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[EncodedClientKey]

  def createConsumerClient(clientSeed: ClientSeed)(implicit contexts: Seq[(String, String)]): Future[Client]

  def createApiClient(clientSeed: ClientSeed)(implicit contexts: Seq[(String, String)]): Future[Client]

  def getClients(
    name: Option[String],
    relationshipIds: Seq[UUID],
    consumerId: UUID,
    purposeId: Option[UUID],
    kind: Option[String],
    limit: Int,
    offset: Int
  )(implicit contexts: Seq[(String, String)]): Future[Clients]
}
