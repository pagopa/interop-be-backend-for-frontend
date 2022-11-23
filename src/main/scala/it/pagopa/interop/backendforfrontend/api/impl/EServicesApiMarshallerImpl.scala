package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.backendforfrontend.api.EservicesApiMarshaller
import it.pagopa.interop.backendforfrontend.model._
import spray.json.DefaultJsonProtocol

object EServicesApiMarshallerImpl extends EservicesApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def toEntityMarshallerCatalogEServices: ToEntityMarshaller[CatalogEServices] =
    sprayJsonMarshaller[CatalogEServices]

  override implicit def toEntityMarshallerCatalogEServiceDescriptor: ToEntityMarshaller[CatalogEServiceDescriptor] =
    sprayJsonMarshaller[CatalogEServiceDescriptor]

  override implicit def toEntityMarshallerProducerEServices: ToEntityMarshaller[ProducerEServices] =
    sprayJsonMarshaller[ProducerEServices]

  override implicit def toEntityMarshallerProducerEServiceDetails: ToEntityMarshaller[ProducerEServiceDetails] =
    sprayJsonMarshaller[ProducerEServiceDetails]

  override implicit def toEntityMarshallerProducerEServiceDescriptor: ToEntityMarshaller[ProducerEServiceDescriptor] =
    sprayJsonMarshaller[ProducerEServiceDescriptor]
}
