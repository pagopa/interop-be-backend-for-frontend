package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.backendforfrontend.api.SupportApiMarshaller
import it.pagopa.interop.backendforfrontend.model._
import spray.json.DefaultJsonProtocol

object SupportApiMarshallerImpl extends SupportApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  implicit def toEntityMarshallerSessionToken: ToEntityMarshaller[SessionToken] = sprayJsonMarshaller[SessionToken]

  override implicit def fromEntityUnmarshallerSAMLTokenRequest: FromEntityUnmarshaller[SAMLTokenRequest] =
    sprayJsonUnmarshaller[SAMLTokenRequest]
}
