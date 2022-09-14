package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.backendforfrontend.api.AgreementsApiMarshaller
import it.pagopa.interop.backendforfrontend.model.{Agreement, AgreementPayload, CreatedResource, Problem}
import spray.json.DefaultJsonProtocol

object AgreementsApiMarshallerImpl extends AgreementsApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def fromEntityUnmarshallerAgreementPayload: FromEntityUnmarshaller[AgreementPayload] =
    sprayJsonUnmarshaller[AgreementPayload]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource] =
    sprayJsonMarshaller[CreatedResource]

  override implicit def toEntityMarshallerAgreement: ToEntityMarshaller[Agreement] = sprayJsonMarshaller[Agreement]

  override implicit def toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]] =
    sprayJsonMarshaller[Seq[Agreement]]
}
