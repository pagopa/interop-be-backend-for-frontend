package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.backendforfrontend.api.EservicesApiMarshaller
import it.pagopa.interop.backendforfrontend.model._
import spray.json.DefaultJsonProtocol

import java.io.File
import java.nio.charset.StandardCharsets
import scala.io.{BufferedSource, Codec}

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

  override implicit def fromEntityUnmarshallerEServiceSeed: FromEntityUnmarshaller[EServiceSeed] =
    sprayJsonUnmarshaller[EServiceSeed]

  override implicit def toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource] =
    sprayJsonMarshaller[CreatedResource]

  override implicit def fromEntityUnmarshallerEServiceDescriptorSeed: FromEntityUnmarshaller[EServiceDescriptorSeed] =
    sprayJsonUnmarshaller[EServiceDescriptorSeed]

  override implicit def toEntityMarshallerFile: ToEntityMarshaller[File] =
    Marshaller.withFixedContentType(ContentTypes.`application/octet-stream`) { f =>
      val source: BufferedSource = scala.io.Source.fromFile(f.getPath)(Codec(StandardCharsets.UTF_8.name))
      val out: String            = source.mkString
      source.close()
      out.getBytes(StandardCharsets.UTF_8.name)
    }
}
