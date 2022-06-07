package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.backendforfrontend.api.AttributesApiMarshaller
import it.pagopa.interop.backendforfrontend.model.{Attribute, AttributesResponse, Problem}
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.backendforfrontend.model.AttributeSeed

object AttributesApiMarshallerImpl extends AttributesApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse] =
    sprayJsonMarshaller[AttributesResponse]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  override implicit def toEntityMarshallerAttribute: ToEntityMarshaller[Attribute] = sprayJsonMarshaller[Attribute]

  override implicit def fromEntityUnmarshallerAttributeSeed: FromEntityUnmarshaller[AttributeSeed] =
    sprayJsonUnmarshaller[AttributeSeed]

}
