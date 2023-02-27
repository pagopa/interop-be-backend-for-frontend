package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.backendforfrontend.api.PurposesApiMarshaller
import it.pagopa.interop.backendforfrontend.model._
import spray.json.DefaultJsonProtocol
import java.io.File

object PurposesApiMarshallerImpl extends PurposesApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def toEntityMarshallerPurposes: ToEntityMarshaller[Purposes] = sprayJsonMarshaller[Purposes]

  override implicit def toEntityMarshallerPurposeVersionResource: ToEntityMarshaller[PurposeVersionResource] =
    sprayJsonMarshaller[PurposeVersionResource]

  override implicit def fromEntityUnmarshallerPurposeVersionSeed: FromEntityUnmarshaller[PurposeVersionSeed] =
    sprayJsonUnmarshaller[PurposeVersionSeed]

  override implicit def toEntityMarshallerFile: ToEntityMarshaller[File] = entityMarshallerFile

  override implicit def fromEntityUnmarshallerWaitingForApprovalPurposeVersionUpdateContentSeed
  : FromEntityUnmarshaller[WaitingForApprovalPurposeVersionUpdateContentSeed] =
    sprayJsonUnmarshaller[WaitingForApprovalPurposeVersionUpdateContentSeed]
}
