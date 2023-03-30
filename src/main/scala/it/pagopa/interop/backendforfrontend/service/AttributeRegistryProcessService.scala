package it.pagopa.interop.backendforfrontend.service

import scala.concurrent.Future

trait AttributeRegistryProcessService {
  def getAttributes(name: Option[String], limit: Int, offset: Int, kinds: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]
}
