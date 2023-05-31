package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.redirect
import akka.http.scaladsl.server.Route
import it.pagopa.interop.backendforfrontend.api.HealthApiService
import it.pagopa.interop.backendforfrontend.model.Problem

class HealthServiceApiImpl extends HealthApiService {

  /** Code: 200, Message: successful operation, DataType: Problem
    */
  override def getStatus()(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val response: Problem = Problem(
      `type` = "about:blank",
      status = StatusCodes.OK.intValue,
      title = StatusCodes.OK.defaultMessage,
      errors = Seq.empty
    )
    getStatus200(response)
  }

  override def moveToSupportPage()(implicit contexts: Seq[(String, String)]): Route = {
    println("SUPPORT TEST INVOCATION")
    redirect("https://selfcare.dev.interop.pagopa.it/ui/it/assistenza/scelta-ente", StatusCodes.MovedPermanently)

  }
}
