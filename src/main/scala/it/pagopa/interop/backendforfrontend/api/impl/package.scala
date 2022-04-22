package it.pagopa.interop.backendforfrontend.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCode
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.commons.utils.errors.ComponentError
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  final val serviceErrorCodePrefix: String = "016"
  final val defaultProblemType: String     = "about:blank"

  implicit def identityTokenFormat: RootJsonFormat[IdentityToken] = jsonFormat1(IdentityToken)
  implicit def sessionTokenFormat: RootJsonFormat[SessionToken]   = jsonFormat1(SessionToken)
  implicit def problemErrorFormat: RootJsonFormat[ProblemError]   = jsonFormat2(ProblemError)
  implicit def problemFormat: RootJsonFormat[Problem]             = jsonFormat5(Problem)

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
