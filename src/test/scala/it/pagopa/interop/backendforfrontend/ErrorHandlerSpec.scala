package it.pagopa.interop.backendforfrontend

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.client.invoker.{ApiError => AgreementProcessError}
import it.pagopa.interop.attributeregistrymanagement.client.invoker.{ApiError => AttributeRegistryError}
import it.pagopa.interop.backendforfrontend.api.impl.{entityMarshallerProblem, problemFormat, problemOf}
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model.{Problem, ProblemError}
import it.pagopa.interop.catalogmanagement.client.invoker.{ApiError => CatalogManagementError}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.GenericError
import it.pagopa.interop.selfcare.partyprocess.client.invoker.{ApiError => PartyProcessError}
import it.pagopa.interop.selfcare.userregistry.client.invoker.{ApiError => UserRegistryError}
import it.pagopa.interop.tenantmanagement.client.invoker.{ApiError => TenantManagementError}
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._

import scala.util.Failure

class ErrorHandlerSpec extends AnyWordSpecLike with ScalatestRouteTest with SprayJsonSupport with DefaultJsonProtocol {

  implicit val contexts: Seq[(String, String)]                                  = Nil
  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog]                 =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)
  implicit val problemMarshaller: ToEntityMarshaller[Problem]                   = entityMarshallerProblem
  implicit def fromResponseUnmarshallerProblem: FromEntityUnmarshaller[Problem] =
    sprayJsonUnmarshaller[Problem]

  val problem: Problem =
    Problem("someType", 404, "An Error occurred", Some("These are the details"), Seq(ProblemError("0001", "det")))

  "Error Handler" should {
    "handle Agreement Process error" in {
      val error = AgreementProcessError(404, message = "An error", responseContent = Some(problem.toJson.compactPrint))

      Get() ~> handleError("error message")(contexts, logger, problemMarshaller)(Failure(error)) ~> check {
        status.intValue shouldBe error.code
        responseAs[Problem] shouldBe problem
      }
    }

    "handle Attribute Registry error" in {
      val error = AttributeRegistryError(404, message = "An error", responseContent = Some(problem.toJson.compactPrint))

      Get() ~> handleError("error message")(contexts, logger, problemMarshaller)(Failure(error)) ~> check {
        status.intValue shouldBe error.code
        responseAs[Problem] shouldBe problem
      }
    }

    "handle Catalog Management error" in {
      val error = CatalogManagementError(404, message = "An error", responseContent = Some(problem.toJson.compactPrint))

      Get() ~> handleError("error message")(contexts, logger, problemMarshaller)(Failure(error)) ~> check {
        status.intValue shouldBe error.code
        responseAs[Problem] shouldBe problem
      }
    }

    "handle Party Process error" in {
      val error = PartyProcessError(404, message = "An error", responseContent = Some(problem.toJson.compactPrint))

      Get() ~> handleError("error message")(contexts, logger, problemMarshaller)(Failure(error)) ~> check {
        status.intValue shouldBe error.code
        responseAs[Problem] shouldBe problem
      }
    }

    "handle Tenant Management error" in {
      val error = TenantManagementError(404, message = "An error", responseContent = Some(problem.toJson.compactPrint))

      Get() ~> handleError("error message")(contexts, logger, problemMarshaller)(Failure(error)) ~> check {
        status.intValue shouldBe error.code
        responseAs[Problem] shouldBe problem
      }
    }

    "handle User Registry error" in {
      val error = UserRegistryError(404, message = "An error", responseContent = Some(problem.toJson.compactPrint))

      Get() ~> handleError("error message")(contexts, logger, problemMarshaller)(Failure(error)) ~> check {
        status.intValue shouldBe error.code
        responseAs[Problem] shouldBe problem
      }
    }

    "return generic error on unexpected error" in {
      val error = new Exception("I'm a generic exception")

      val message = "error message"

      Get() ~> handleError(message)(contexts, logger, problemMarshaller)(Failure(error)) ~> check {
        status.intValue shouldBe 500
        responseAs[Problem] shouldBe problemOf(StatusCodes.InternalServerError, GenericError(message))
      }
    }
  }
}
