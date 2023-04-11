package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.attributeregistryprocess.client.model.Attributes
import it.pagopa.interop.attributeregistryprocess.client.model.AttributeKind

import scala.concurrent.Future

trait AttributeRegistryProcessService {
  def getAttributes(q: Option[String], limit: Int, offset: Int, kinds: Seq[AttributeKind])(implicit
    contexts: Seq[(String, String)]
  ): Future[Attributes]
}
