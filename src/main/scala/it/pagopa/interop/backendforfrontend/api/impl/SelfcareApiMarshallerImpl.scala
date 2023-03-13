package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.backendforfrontend.api.SelfcareApiMarshaller
import it.pagopa.interop.backendforfrontend.model._
import spray.json._

object SelfcareApiMarshallerImpl extends SelfcareApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def toEntityMarshallerSelfcareProductarray: ToEntityMarshaller[Seq[SelfcareProduct]] =
    sprayJsonMarshaller[Seq[SelfcareProduct]]

  override implicit def toEntityMarshallerSelfcareInstitutionarray: ToEntityMarshaller[Seq[SelfcareInstitution]] =
    sprayJsonMarshaller[Seq[SelfcareInstitution]]
}
