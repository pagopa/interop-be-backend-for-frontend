package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.backendforfrontend.api.ClientsApiMarshaller
import it.pagopa.interop.backendforfrontend.model.{Problem, PurposeAdditionDetailsSeed}
import spray.json.DefaultJsonProtocol

object ClientsApiMarshallerImpl extends ClientsApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def fromEntityUnmarshallerPurposeAdditionDetailsSeed
    : FromEntityUnmarshaller[PurposeAdditionDetailsSeed] = sprayJsonUnmarshaller[PurposeAdditionDetailsSeed]

}
