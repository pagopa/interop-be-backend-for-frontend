package it.pagopa.interop.backendforfrontend

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.impl.entityMarshallerProblem
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model.Problem
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import org.scalatest.wordspec.AnyWordSpecLike

class ErrorHandlerSpec extends AnyWordSpecLike {

  implicit val contexts: Seq[(String, String)]                  = Nil
  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)
  implicit val problemMarshaller: ToEntityMarshaller[Problem]   = entityMarshallerProblem

  "Error Handler" should {
    "handle Agreement Process error" in {
      handleError("error message")
    }

    "handle Attribute Registry error" in {}
    "handle Catalog Management error" in {}
    "handle Party Process error" in {}
    "handle Tenant Management error" in {}
    "return generic error on unexpected error" in {}
  }
}
