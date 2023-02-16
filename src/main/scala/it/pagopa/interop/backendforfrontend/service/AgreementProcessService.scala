package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.agreementprocess.client.model._

import java.util.UUID
import scala.concurrent.Future

trait AgreementProcessService {

  def createAgreement(seed: AgreementPayload)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def deleteAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]
  def getAgreementById(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def activateAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def submitAgreement(agreementId: UUID, payload: AgreementSubmissionPayload)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement]
  def suspendAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def updateAgreement(agreementId: UUID, agreementUpdatePayload: AgreementUpdatePayload)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement]
  def upgradeAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def rejectAgreement(agreementId: UUID, payload: AgreementRejectionPayload)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement]
  def cloneAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]

  def getAgreements(
    producersIds: Seq[UUID] = Seq.empty,
    consumersIds: Seq[UUID] = Seq.empty,
    eservicesIds: Seq[UUID] = Seq.empty,
    descriptorsIds: Seq[UUID] = Seq.empty,
    states: Seq[AgreementState] = Seq.empty,
    limit: Int,
    offset: Int = 0,
    showOnlyUpgradeable: Option[Boolean] = Some(false)
  )(implicit contexts: Seq[(String, String)]): Future[Agreements]

  def addConsumerDocument(agreementId: UUID, seed: DocumentSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Document]

  def getConsumerDocument(agreementId: UUID, documentId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Document]
  def removeConsumerDocument(agreementId: UUID, documentId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]

  def getAgreementEServices(
    eServiceName: Option[String],
    producersIds: Seq[UUID] = Seq.empty,
    consumersIds: Seq[UUID] = Seq.empty,
    limit: Int,
    offset: Int
  )(implicit contexts: Seq[(String, String)]): Future[CompactEServices]
}
