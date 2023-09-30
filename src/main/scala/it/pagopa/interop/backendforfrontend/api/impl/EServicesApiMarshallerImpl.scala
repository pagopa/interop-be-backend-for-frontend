package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.backendforfrontend.api.EservicesApiMarshaller
import it.pagopa.interop.backendforfrontend.model._
import spray.json.DefaultJsonProtocol

import java.io.File

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

  override implicit def fromEntityUnmarshallerUpdateEServiceDescriptorSeed
    : FromEntityUnmarshaller[UpdateEServiceDescriptorSeed] = sprayJsonUnmarshaller[UpdateEServiceDescriptorSeed]

  override implicit def fromEntityUnmarshallerEServiceDescriptorSeed: FromEntityUnmarshaller[EServiceDescriptorSeed] =
    sprayJsonUnmarshaller[EServiceDescriptorSeed]

  override implicit def fromEntityUnmarshallerUpdateEServiceDescriptorDocumentSeed
    : FromEntityUnmarshaller[UpdateEServiceDescriptorDocumentSeed] =
    sprayJsonUnmarshaller[UpdateEServiceDescriptorDocumentSeed]

  override implicit def toEntityMarshallerEServiceDoc: ToEntityMarshaller[EServiceDoc] =
    sprayJsonMarshaller[EServiceDoc]

  override implicit def fromEntityUnmarshallerUpdateEServiceSeed: FromEntityUnmarshaller[UpdateEServiceSeed] =
    sprayJsonUnmarshaller[UpdateEServiceSeed]

  override implicit def toEntityMarshallerCreatedEServiceDescriptor: ToEntityMarshaller[CreatedEServiceDescriptor] =
    sprayJsonMarshaller[CreatedEServiceDescriptor]

  override implicit def fromEntityUnmarshallerEServiceRiskAnalysisSeed
    : FromEntityUnmarshaller[EServiceRiskAnalysisSeed] =
    sprayJsonUnmarshaller[EServiceRiskAnalysisSeed]

  override implicit def toEntityMarshallerEServiceRiskAnalysis: ToEntityMarshaller[EServiceRiskAnalysis] =
    sprayJsonMarshaller[EServiceRiskAnalysis]

  override implicit def toEntityMarshallerFile: ToEntityMarshaller[File] = entityMarshallerFile
}
