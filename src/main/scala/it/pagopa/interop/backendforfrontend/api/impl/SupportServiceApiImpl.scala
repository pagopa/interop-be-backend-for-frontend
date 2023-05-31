package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.model.headers.{HttpCookie, Location, `Set-Cookie`}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import it.pagopa.interop.backendforfrontend.api.SupportApiService
import it.pagopa.interop.backendforfrontend.model.SAMLResponse
import it.pagopa.interop.commons.utils.TypeConversions.StringOps

import scala.util.{Failure, Success}

class SupportServiceApiImpl extends SupportApiService {

  override def moveToSupportPage(saml: SAMLResponse)(implicit contexts: Seq[(String, String)]): Route = {
    println("SUPPORT TEST INVOCATION")

    saml.response.encodeBase64 match {
      case Success(base64) =>
        val redirectUrl =
          s"https://selfcare.dev.interop.pagopa.it/ui/it/assistenza/scelta-ente"
        val samlCookie  = HttpCookie("saml2", base64)
          .withSecure(true)
          .withHttpOnly(true)
        val jwt         = HttpCookie("token", "jwt")
          .withSecure(true)
          .withHttpOnly(true)
        val headers     = List(Location(redirectUrl), `Set-Cookie`(samlCookie), `Set-Cookie`(jwt))

        complete(
          HttpResponse(
            status = StatusCodes.MovedPermanently,
            headers = headers,
            entity = StatusCodes.MovedPermanently.htmlTemplate match {
              case ""       => HttpEntity.Empty
              case template => HttpEntity(ContentTypes.`text/html(UTF-8)`, template format redirectUrl)
            }
          )
        )
      case Failure(ex)     =>
        ex.printStackTrace()
        complete(StatusCodes.BadRequest)
    }

  }
}
