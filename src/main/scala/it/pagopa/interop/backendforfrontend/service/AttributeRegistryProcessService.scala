package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.attributeregistryprocess.client.model.Attributes

import scala.concurrent.Future

trait AttributeRegistryProcessService {
  def getAttributes(name: Option[String], limit: Int, offset: Int, kinds: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Attributes]
}
