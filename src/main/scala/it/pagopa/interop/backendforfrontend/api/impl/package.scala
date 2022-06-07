package it.pagopa.interop.backendforfrontend.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCode
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.commons.utils.errors.ComponentError
import it.pagopa.interop.commons.utils.SprayCommonFormats.{offsetDateTimeFormat, uuidFormat}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  final val serviceErrorCodePrefix: String = "016"
  final val defaultProblemType: String     = "about:blank"

  implicit val identityTokenFormat: RootJsonFormat[IdentityToken]       = jsonFormat1(IdentityToken)
  implicit val sessionTokenFormat: RootJsonFormat[SessionToken]         = jsonFormat1(SessionToken)
  implicit val productInfoFormat: RootJsonFormat[ProductInfo]           = jsonFormat3(ProductInfo)
  implicit val relationshipInfoFormat: RootJsonFormat[RelationshipInfo] = jsonFormat11(RelationshipInfo)

  implicit val institutionAttributeFormat: RootJsonFormat[InstitutionAttribute] = jsonFormat3(InstitutionAttribute)
  implicit val institutionFormat: RootJsonFormat[Institution]                   = jsonFormat11(Institution)

  implicit val attributeFormat: RootJsonFormat[Attribute]                   = jsonFormat7(Attribute)
  implicit val attributesResponseFormat: RootJsonFormat[AttributesResponse] = jsonFormat1(AttributesResponse)

  implicit val attributeSeedFormat: RootJsonFormat[AttributeSeed] = jsonFormat5(AttributeSeed)

  implicit val problemErrorFormat: RootJsonFormat[ProblemError] = jsonFormat2(ProblemError)
  implicit val problemFormat: RootJsonFormat[Problem]           = jsonFormat5(Problem)

  def problemOf(httpError: StatusCode, error: ComponentError, defaultMessage: String = "Unknown error"): Problem =
    Problem(
      `type` = defaultProblemType,
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors = Seq(
        ProblemError(
          code = s"$serviceErrorCodePrefix-${error.code}",
          detail = Option(error.getMessage).getOrElse(defaultMessage)
        )
      )
    )

}
