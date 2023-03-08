package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller

import it.pagopa.interop.backendforfrontend.api.ClientsApiMarshaller
import it.pagopa.interop.backendforfrontend.model.{Problem, Clients}
import spray.json._

object ClientsApiMarshallerImpl extends ClientsApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def toEntityMarshallerClients: ToEntityMarshaller[Clients] = sprayJsonMarshaller[Clients]

}
