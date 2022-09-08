package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.agreementprocess.client.model.{Agreement, AgreementPayload}

import scala.concurrent.Future

trait AgreementProcessService {

  def createAgreement(seed: AgreementPayload)(implicit contexts: Seq[(String, String)]): Future[Agreement]

}
