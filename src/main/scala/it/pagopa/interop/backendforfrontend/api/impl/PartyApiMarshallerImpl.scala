package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.backendforfrontend.api.PartyApiMarshaller
import it.pagopa.interop.backendforfrontend.model._
import spray.json._

object PartyApiMarshallerImpl extends PartyApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerRelationshipInfo: ToEntityMarshaller[RelationshipInfo] =
    sprayJsonMarshaller[RelationshipInfo]

  override implicit def toEntityMarshallerRelationshipInfoarray: ToEntityMarshaller[Seq[RelationshipInfo]] =
    sprayJsonMarshaller[Seq[RelationshipInfo]]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem
}
