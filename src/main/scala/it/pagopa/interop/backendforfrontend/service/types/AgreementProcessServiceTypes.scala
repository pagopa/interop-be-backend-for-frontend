package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.agreementprocess.client.{model => AgreementProcess}
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.model.AgreementState._

object AgreementProcessServiceTypes {

  implicit class AgreementPayloadConverter(private val seed: AgreementPayload) extends AnyVal {
    def toSeed: AgreementProcess.AgreementPayload =
      AgreementProcess.AgreementPayload(eserviceId = seed.eserviceId, descriptorId = seed.descriptorId)
  }

  implicit class AgreementStateConverter(private val s: AgreementProcess.AgreementState) extends AnyVal {
    def toApi: AgreementState = s match {
      case AgreementProcess.AgreementState.DRAFT                        => DRAFT
      case AgreementProcess.AgreementState.ACTIVE                       => ACTIVE
      case AgreementProcess.AgreementState.ARCHIVED                     => ARCHIVED
      case AgreementProcess.AgreementState.PENDING                      => PENDING
      case AgreementProcess.AgreementState.SUSPENDED                    => SUSPENDED
      case AgreementProcess.AgreementState.MISSING_CERTIFIED_ATTRIBUTES => MISSING_CERTIFIED_ATTRIBUTES
    }
  }

  implicit class DocumentConverter(private val doc: AgreementProcess.Document) extends AnyVal {
    def toApi: Document =
      Document(
        id = doc.id,
        name = doc.name,
        prettyName = doc.prettyName,
        contentType = doc.contentType,
        createdAt = doc.createdAt
      )
  }
}
