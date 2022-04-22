package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.backendforfrontend.api.AuthorizationApiMarshaller
import it.pagopa.interop.backendforfrontend.model.{IdentityToken, Problem, SessionToken}
import spray.json._

object AuthorizationApiMarshallerImpl
    extends AuthorizationApiMarshaller
    with SprayJsonSupport
    with DefaultJsonProtocol {

  override implicit def fromEntityUnmarshallerIdentityToken: FromEntityUnmarshaller[IdentityToken] =
    sprayJsonUnmarshaller[IdentityToken]

  override implicit def toEntityMarshallerSessionToken: ToEntityMarshaller[SessionToken] =
    sprayJsonMarshaller[SessionToken]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]
}
