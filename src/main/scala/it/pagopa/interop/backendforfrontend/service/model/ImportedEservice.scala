package it.pagopa.interop.backendforfrontend.service.model

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import it.pagopa.interop.backendforfrontend.model.{AgreementApprovalPolicy, EServiceMode, EServiceTechnology}

final case class ImportedDoc(prettyName: String, name: String)
final case class ImportedDescriptor(
                       interface: Option[ImportedDoc],
                       docs: Seq[ImportedDoc],
                       audience: Seq[String],
                       voucherLifespan: Int,
                       dailyCallsPerConsumer: Int,
                       dailyCallsTotal: Int,
                       description: Option[String],
                       agreementApprovalPolicy: AgreementApprovalPolicy
                     )
final case class ImportedSingleAnswer(key: String, value: Option[String])
final case class ImportedMultiAnswer(key: String, values: Seq[String])
final case class ImportedRiskAnalysis(version: String, singleAnswers: Seq[ImportedSingleAnswer], multiAnswers: Seq[ImportedMultiAnswer])

final case class ImportedEservice (
                                    name: String,
                                    description: String,
                                    technology: EServiceTechnology,
                                    mode: EServiceMode,
                                    descriptor: ImportedDescriptor,
                                    riskAnalysis: Option[ImportedRiskAnalysis]
                                  )

object ImportedEservice {
  implicit val decoder: Decoder[ImportedEservice] = deriveDecoder
  implicit val encoder: Encoder[ImportedEservice] = deriveEncoder
}