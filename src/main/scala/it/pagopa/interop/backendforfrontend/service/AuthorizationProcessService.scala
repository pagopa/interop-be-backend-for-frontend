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

  def getClientKeys(clientId: UUID)(implicit contexts: Seq[(String, String)]): Future[ReadClientKeys]

}
