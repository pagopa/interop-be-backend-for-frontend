package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.agreementprocess.client.{model => AgreementProcess}
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.model.AgreementState._

object AgreementProcessServiceTypes {

  implicit class AgreementPayloadConverter(private val seed: AgreementPayload) extends AnyVal {
    def toSeed: AgreementProcess.AgreementPayload =
      AgreementProcess.AgreementPayload(eserviceId = seed.eserviceId, descriptorId = seed.descriptorId)
  }

  implicit class AgreementSubmissionPayloadConverter(private val seed: AgreementSubmissionPayload) extends AnyVal {
    def toSeed: AgreementProcess.AgreementSubmissionPayload =
      AgreementProcess.AgreementSubmissionPayload(consumerNotes = seed.consumerNotes)
  }

  implicit class AgreementRejectionPayloadConverter(private val payload: AgreementRejectionPayload) extends AnyVal {
    def toSeed: AgreementProcess.AgreementRejectionPayload =
      AgreementProcess.AgreementRejectionPayload(reason = payload.reason)
  }

  implicit class AgreementUpdatePayloadConverter(private val payload: AgreementUpdatePayload) extends AnyVal {
    def toSeed: AgreementProcess.AgreementUpdatePayload =
      AgreementProcess.AgreementUpdatePayload(consumerNotes = payload.consumerNotes)
  }

  implicit class AgreementStateConverter(private val s: AgreementProcess.AgreementState) extends AnyVal {
    def toApi: AgreementState = s match {
      case AgreementProcess.AgreementState.DRAFT                        => DRAFT
      case AgreementProcess.AgreementState.ACTIVE                       => ACTIVE
      case AgreementProcess.AgreementState.ARCHIVED                     => ARCHIVED
      case AgreementProcess.AgreementState.PENDING                      => PENDING
      case AgreementProcess.AgreementState.SUSPENDED                    => SUSPENDED
      case AgreementProcess.AgreementState.MISSING_CERTIFIED_ATTRIBUTES => MISSING_CERTIFIED_ATTRIBUTES
      case AgreementProcess.AgreementState.REJECTED                     => REJECTED
    }
  }

  implicit class AgreementStateObjectConverter(private val s: AgreementProcess.AgreementState.type) extends AnyVal {
    def fromApi(s: AgreementState): AgreementProcess.AgreementState = s match {
      case DRAFT                        => AgreementProcess.AgreementState.DRAFT
      case ACTIVE                       => AgreementProcess.AgreementState.ACTIVE
      case ARCHIVED                     => AgreementProcess.AgreementState.ARCHIVED
      case PENDING                      => AgreementProcess.AgreementState.PENDING
      case SUSPENDED                    => AgreementProcess.AgreementState.SUSPENDED
      case MISSING_CERTIFIED_ATTRIBUTES => AgreementProcess.AgreementState.MISSING_CERTIFIED_ATTRIBUTES
      case REJECTED                     => AgreementProcess.AgreementState.REJECTED
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
