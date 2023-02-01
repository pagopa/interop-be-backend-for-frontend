package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.catalogprocess.client.model._

import java.util.UUID
import scala.concurrent.Future

trait CatalogProcessService {

  def createEService(eServiceSeed: EServiceSeed)(implicit contexts: Seq[(String, String)]): Future[EService]

  def activateDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]

  def getEServices(
    name: Option[String] = None,
    eServicesIds: Seq[UUID],
    producersIds: Seq[UUID],
    states: Seq[EServiceDescriptorState],
    offset: Int,
    limit: Int
  )(implicit contexts: Seq[(String, String)]): Future[EServices]

  def getEServiceById(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService]
}
