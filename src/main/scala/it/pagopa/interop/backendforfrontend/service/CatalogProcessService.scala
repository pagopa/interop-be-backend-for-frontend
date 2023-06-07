package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.catalogprocess.client.model._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
trait CatalogProcessService {

  def cloneEServiceByDescriptor(eServiceId: UUID, descriptorId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[EService]

  def createEService(eServiceSeed: EServiceSeed)(implicit contexts: Seq[(String, String)]): Future[EService]

  def activateDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]

  def publishDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]

  def updateEServiceById(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[EService]

  def getEServices(
    name: Option[String] = None,
    eServicesIds: Seq[UUID],
    producersIds: Seq[UUID],
    agreementStates: Seq[AgreementState],
    states: Seq[EServiceDescriptorState],
    offset: Int,
    limit: Int
  )(implicit contexts: Seq[(String, String)]): Future[EServices]

  def getEServiceById(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService]

  def updateDraftDescriptor(
    eServiceId: UUID,
    descriptorId: UUID,
    updateEServiceDescriptorSeed: UpdateEServiceDescriptorSeed
  )(implicit contexts: Seq[(String, String)]): Future[EService]

  def createDescriptor(eServiceId: UUID, eServiceDescriptorSeed: EServiceDescriptorSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[EServiceDescriptor]

  def suspendDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]

  def updateEServiceDocumentById(
    eServiceId: String,
    descriptorId: String,
    documentId: String,
    updateEServiceDescriptorDocumentSeed: UpdateEServiceDescriptorDocumentSeed
  )(implicit contexts: Seq[(String, String)]): Future[EServiceDoc]

  def deleteEServiceDocumentById(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]

  def createEServiceDocument(eServiceId: UUID, descriptorId: UUID, documentSeed: CreateEServiceDescriptorDocumentSeed)(
    implicit contexts: Seq[(String, String)]
  ): Future[EService]

  def getEServiceDocumentById(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[EServiceDoc]

  def deleteDraft(eServiceId: String, descriptorId: String)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def deleteEService(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]

  def getEServiceConsumers(eServiceId: UUID, offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[EServiceConsumers]

  def getAllEServiceConsumers(
    eServiceId: UUID
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[List[EServiceConsumer]] = {

    def getEServiceConsumersFrom(offset: Int): Future[List[EServiceConsumer]] =
      getEServiceConsumers(eServiceId = eServiceId, limit = 50, offset = offset)
        .map(_.results.toList)

    def go(start: Int)(as: List[EServiceConsumer]): Future[List[EServiceConsumer]] =
      getEServiceConsumersFrom(start).flatMap(esec =>
        if (esec.size < 50) Future.successful(as ++ esec) else go(start + 50)(as ++ esec)
      )

    go(0)(Nil)
  }
}
