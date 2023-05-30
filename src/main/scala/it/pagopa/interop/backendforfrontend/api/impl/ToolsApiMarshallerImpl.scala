package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.backendforfrontend.api.ToolsApiMarshaller
import it.pagopa.interop.backendforfrontend.model.{Problem, TokenGenerationValidationResult}
import spray.json._

object ToolsApiMarshallerImpl extends ToolsApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def toEntityMarshallerTokenGenerationValidationResult
    : ToEntityMarshaller[TokenGenerationValidationResult] = sprayJsonMarshaller[TokenGenerationValidationResult]
}
