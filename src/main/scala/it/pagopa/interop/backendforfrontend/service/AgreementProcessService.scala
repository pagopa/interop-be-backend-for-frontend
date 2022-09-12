package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.agreementprocess.client.model.{Agreement, AgreementPayload}

import java.util.UUID
import scala.concurrent.Future

trait AgreementProcessService {

  def createAgreement(seed: AgreementPayload)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def getAgreementById(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def activateAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def suspendAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]

}
