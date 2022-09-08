package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.agreementprocess.client.{model => AgreementProcess}
import it.pagopa.interop.backendforfrontend.model._

object AgreementProcessServiceTypes {

  implicit class AgreementPayloadConverter(private val seed: AgreementPayload) extends AnyVal {
    def toSeed: AgreementProcess.AgreementPayload =
      AgreementProcess.AgreementPayload(eserviceId = seed.eserviceId, descriptorId = seed.descriptorId)
  }

}
