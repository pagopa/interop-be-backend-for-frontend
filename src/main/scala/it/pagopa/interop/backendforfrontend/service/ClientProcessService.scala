package it.pagopa.interop.backendforfrontend.service

import scala.concurrent.Future

trait ClientProcessService {

  def deleteClient(clientId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]
}
