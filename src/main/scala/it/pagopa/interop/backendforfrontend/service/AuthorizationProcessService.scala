package it.pagopa.interop.backendforfrontend.service

import scala.concurrent.Future

trait AuthorizationProcessService {

  def deleteClient(clientId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]
}
