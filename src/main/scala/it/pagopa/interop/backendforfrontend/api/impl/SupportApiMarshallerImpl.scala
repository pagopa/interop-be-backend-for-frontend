package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import it.pagopa.interop.backendforfrontend.api.SupportApiMarshaller
import it.pagopa.interop.backendforfrontend.model.SAMLResponse

object SupportApiMarshallerImpl extends SupportApiMarshaller {

  override implicit def fromEntityUnmarshallerSAMLResponse: FromEntityUnmarshaller[SAMLResponse] =
    Unmarshaller.stringUnmarshaller
      .forContentTypes(MediaTypes.`text/xml`, MediaTypes.`application/xml`)
      .map(SAMLResponse)

}
