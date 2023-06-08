package it.pagopa.interop.backendforfrontend.api.impl

import spray.json.DefaultJsonProtocol
import it.pagopa.interop.backendforfrontend.api.SupportApiMarshaller
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import it.pagopa.interop.backendforfrontend.model._

object SupportApiMarshallerImpl extends SupportApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def fromEntityUnmarshallerSAMLResponse: FromEntityUnmarshaller[SAMLResponse] = {
    Unmarshaller.stringUnmarshaller
      .forContentTypes(MediaTypes.`text/xml`, MediaTypes.`application/xml`)
      .map(SAMLResponse)
  }

  implicit def toEntityMarshallerTestSAML: ToEntityMarshaller[TestSAML] = sprayJsonMarshaller[TestSAML]
}
