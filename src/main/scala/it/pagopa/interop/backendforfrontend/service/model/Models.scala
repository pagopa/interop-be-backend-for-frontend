package it.pagopa.interop.backendforfrontend.service.model

import spray.json.RootJsonFormat
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

final case class Role(partyRole: String, role: String)
final case class Organization(id: String, name: String, roles: Seq[Role])

object JsonFormats extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val roleFormat: RootJsonFormat[Role]                 = jsonFormat2(Role)
  implicit val organizationFormat: RootJsonFormat[Organization] = jsonFormat3(Organization)
}
