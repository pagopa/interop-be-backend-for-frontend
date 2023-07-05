package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.backendforfrontend.api.PrivacyNoticesApiMarshaller
import it.pagopa.interop.backendforfrontend.model._
import spray.json.DefaultJsonProtocol
import java.io.File

object PrivacyNoticesApiMarshallerImpl
    extends PrivacyNoticesApiMarshaller
    with SprayJsonSupport
    with DefaultJsonProtocol {

  override implicit def toEntityMarshallerFile: ToEntityMarshaller[File]       = entityMarshallerFile
  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def toEntityMarshallerPrivacyNotice: ToEntityMarshaller[PrivacyNotice] =
    sprayJsonMarshaller[PrivacyNotice]

  override implicit def fromEntityUnmarshallerPrivacyNoticeSeed: FromEntityUnmarshaller[PrivacyNoticeSeed] =
    sprayJsonUnmarshaller[PrivacyNoticeSeed]
}
