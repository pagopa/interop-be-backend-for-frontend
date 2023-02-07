package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.catalogmanagement.client.model.EService
import it.pagopa.interop.catalogmanagement.client.model.EServiceDoc
import java.util.UUID
import scala.concurrent.Future

trait CatalogManagementService {

  def getEService(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService]

  def getEServiceDocument(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[EServiceDoc]

}
