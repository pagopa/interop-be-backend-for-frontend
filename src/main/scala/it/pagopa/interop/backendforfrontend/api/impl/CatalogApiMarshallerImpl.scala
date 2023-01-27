package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.backendforfrontend.api.CatalogApiMarshaller
import it.pagopa.interop.backendforfrontend.model.{EServiceSeed, OldEService, Problem}
import spray.json.DefaultJsonProtocol

object CatalogApiMarshallerImpl extends CatalogApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {
  override implicit def fromEntityUnmarshallerEServiceSeed: FromEntityUnmarshaller[EServiceSeed] =
    sprayJsonUnmarshaller[EServiceSeed]

  override implicit def toEntityMarshallerOldEService: ToEntityMarshaller[OldEService] =
    sprayJsonMarshaller[OldEService]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem
}
