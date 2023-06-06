package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, redirect}
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
          s"https://selfcare.dev.interop.pagopa.it/ui/it/assistenza/scelta-ente#saml2=$base64&jwt=<add_jwt>"
        redirect(redirectUrl, StatusCodes.MovedPermanently)
      case Failure(ex)     =>
        ex.printStackTrace()
        complete(StatusCodes.BadRequest)
    }

  }
}
