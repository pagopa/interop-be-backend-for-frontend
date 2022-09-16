package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.backendforfrontend.api.TenantsApiMarshaller
import it.pagopa.interop.backendforfrontend.model.{CertifiedAttributesResponse, Problem}
import spray.json.DefaultJsonProtocol

object TenantsApiMarshallerImpl extends TenantsApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def toEntityMarshallerCertifiedAttributesResponse: ToEntityMarshaller[CertifiedAttributesResponse] =
    sprayJsonMarshaller[CertifiedAttributesResponse]

}
