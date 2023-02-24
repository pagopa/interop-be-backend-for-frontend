package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.backendforfrontend.api.AgreementsApiMarshaller
import it.pagopa.interop.backendforfrontend.model._
import spray.json.DefaultJsonProtocol
import java.io.File

object AgreementsApiMarshallerImpl extends AgreementsApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerAgreements: ToEntityMarshaller[Agreements] = sprayJsonMarshaller[Agreements]

  override implicit def fromEntityUnmarshallerAgreementPayload: FromEntityUnmarshaller[AgreementPayload] =
    sprayJsonUnmarshaller[AgreementPayload]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource] =
    sprayJsonMarshaller[CreatedResource]

  override implicit def toEntityMarshallerAgreement: ToEntityMarshaller[Agreement] = sprayJsonMarshaller[Agreement]

  override implicit def fromEntityUnmarshallerAgreementRejectionPayload
    : FromEntityUnmarshaller[AgreementRejectionPayload] = sprayJsonUnmarshaller[AgreementRejectionPayload]

  override implicit def toEntityMarshallerFile: ToEntityMarshaller[File] = entityMarshallerFile

  override implicit def fromEntityUnmarshallerAgreementSubmissionPayload
    : FromEntityUnmarshaller[AgreementSubmissionPayload] = sprayJsonUnmarshaller[AgreementSubmissionPayload]

  override implicit def fromEntityUnmarshallerAgreementUpdatePayload: FromEntityUnmarshaller[AgreementUpdatePayload] =
    sprayJsonUnmarshaller[AgreementUpdatePayload]

  override implicit def toEntityMarshallerCompactEServicesLight: ToEntityMarshaller[CompactEServicesLight] =
    sprayJsonMarshaller[CompactEServicesLight]

  implicit def toEntityMarshallerCompactOrganizations: ToEntityMarshaller[CompactOrganizations] =
    sprayJsonMarshaller[CompactOrganizations]
}
