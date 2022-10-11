package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.agreementprocess.client.model._

import java.util.UUID
import scala.concurrent.Future

trait AgreementProcessService {

  def createAgreement(seed: AgreementPayload)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def deleteAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]
  def getAgreementById(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def activateAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def submitAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def suspendAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def upgradeAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def rejectAgreement(agreementId: UUID, payload: AgreementRejectionPayload)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement]

  def getAgreements(
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eServiceId: Option[String] = None,
    descriptorId: Option[String] = None,
    states: Seq[AgreementState],
    latest: Option[Boolean] = None
  )(implicit contexts: Seq[(String, String)]): Future[Seq[Agreement]]

}
