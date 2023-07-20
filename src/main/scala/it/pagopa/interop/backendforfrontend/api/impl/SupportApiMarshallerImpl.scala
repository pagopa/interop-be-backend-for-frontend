package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.backendforfrontend.api.SupportApiMarshaller
import it.pagopa.interop.backendforfrontend.model._
import spray.json.DefaultJsonProtocol

object SupportApiMarshallerImpl extends SupportApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  implicit def toEntityMarshallerSessionToken: ToEntityMarshaller[SessionToken] = sprayJsonMarshaller[SessionToken]

}
