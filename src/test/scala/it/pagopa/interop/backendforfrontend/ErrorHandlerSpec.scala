package it.pagopa.interop.backendforfrontend

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{StatusCode, StatusCodes, HttpHeader}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.client.invoker.{ApiError => AgreementProcessError}
import it.pagopa.interop.attributeregistryprocess.client.invoker.{ApiError => AttributeRegistryError}
import it.pagopa.interop.backendforfrontend.api.impl.{problemFormat, problemOf}
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model.{Problem, ProblemError}
import it.pagopa.interop.catalogprocess.client.invoker.{ApiError => CatalogProcessError}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.GenericError
import it.pagopa.interop.commons.utils.errors.Problem.defaultProblemType
import it.pagopa.interop.selfcare.partyprocess.client.invoker.{ApiError => PartyProcessError}
import it.pagopa.interop.selfcare.userregistry.client.invoker.{ApiError => UserRegistryError}
import it.pagopa.interop.selfcare.v2.client.invoker.{ApiError => SelfcareError}
import it.pagopa.interop.tenantprocess.client.invoker.{ApiError => TenantProcessError}
import it.pagopa.interop.authorizationprocess.client.invoker.{ApiError => AuthorizationProcessError}

import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._

import scala.util.Failure

class ErrorHandlerSpec extends AnyWordSpecLike with ScalatestRouteTest with SprayJsonSupport with DefaultJsonProtocol {

  implicit val contexts: Seq[(String, String)]                                  = Nil
  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog]                 =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)
  implicit def fromResponseUnmarshallerProblem: FromEntityUnmarshaller[Problem] =
    sprayJsonUnmarshaller[Problem]

  val problemError: ProblemError = ProblemError("999-0001", "det")
  val problem: Problem           =
    Problem("someType", 404, "An Error occurred", None, Some("These are the details"), Seq(problemError))
  def expectedProblem(httpStatus: StatusCode, errorCode: String, errorMessage: String): Problem = Problem(
    `type` = defaultProblemType,
    status = httpStatus.intValue,
    title = httpStatus.defaultMessage,
    errors = Seq(ProblemError(code = errorCode, detail = errorMessage))
  )
  val headersFromContext = List[HttpHeader](RawHeader("header-1", "value-1"), RawHeader("header-2", "value-2"))

  "Error Handler" should {

    "handle Authorization Process error" in {
      val error =
        AuthorizationProcessError(404, message = "An error", responseContent = Some(problem.toJson.compactPrint))

      Get() ~> handleError("error message", headersFromContext)(contexts, logger)(Failure(error)) ~> check {
        status.intValue shouldBe error.code
        responseAs[Problem] shouldBe expectedProblem(StatusCodes.NotFound, problemError.code, problemError.detail)
        headers should contain allElementsOf headersFromContext
      }
    }

    "handle Agreement Process error" in {
      val error = AgreementProcessError(404, message = "An error", responseContent = Some(problem.toJson.compactPrint))

      Get() ~> handleError("error message", headersFromContext)(contexts, logger)(Failure(error)) ~> check {
        status.intValue shouldBe error.code
        responseAs[Problem] shouldBe expectedProblem(StatusCodes.NotFound, problemError.code, problemError.detail)
        headers should contain allElementsOf headersFromContext
      }
    }

    "handle Catalog Process error" in {
      val error = CatalogProcessError(404, message = "An error", responseContent = Some(problem.toJson.compactPrint))

      Get() ~> handleError("error message", headersFromContext)(contexts, logger)(Failure(error)) ~> check {
        status.intValue shouldBe error.code
        responseAs[Problem] shouldBe expectedProblem(StatusCodes.NotFound, problemError.code, problemError.detail)
        headers should contain allElementsOf headersFromContext
      }
    }

    "handle Attribute Registry error" in {
      val error = AttributeRegistryError(404, message = "An error", responseContent = Some(problem.toJson.compactPrint))

      Get() ~> handleError("error message", headersFromContext)(contexts, logger)(Failure(error)) ~> check {
        status.intValue shouldBe error.code
        responseAs[Problem] shouldBe expectedProblem(StatusCodes.NotFound, problemError.code, problemError.detail)
        headers should contain allElementsOf headersFromContext
      }
    }

    "handle Party Process error" in {
      val error = PartyProcessError(404, message = "An error", responseContent = Some(problem.toJson.compactPrint))

      Get() ~> handleError("error message", headersFromContext)(contexts, logger)(Failure(error)) ~> check {
        status.intValue shouldBe error.code
        responseAs[Problem] shouldBe expectedProblem(StatusCodes.NotFound, problemError.code, problemError.detail)
        headers should contain allElementsOf headersFromContext
      }
    }

    "handle Tenant Process error" in {
      val error = TenantProcessError(404, message = "An error", responseContent = Some(problem.toJson.compactPrint))

      Get() ~> handleError("error message", headersFromContext)(contexts, logger)(Failure(error)) ~> check {
        status.intValue shouldBe error.code
        responseAs[Problem] shouldBe expectedProblem(StatusCodes.NotFound, problemError.code, problemError.detail)
        headers should contain allElementsOf headersFromContext
      }
    }

    "handle User Registry error" in {
      val error = UserRegistryError(404, message = "An error", responseContent = Some(problem.toJson.compactPrint))

      Get() ~> handleError("error message", headersFromContext)(contexts, logger)(Failure(error)) ~> check {
        status.intValue shouldBe error.code
        responseAs[Problem] shouldBe expectedProblem(StatusCodes.NotFound, problemError.code, problemError.detail)
        headers should contain allElementsOf headersFromContext
      }
    }

    "handle Selfcare Client error" in {
      val error = SelfcareError(404, message = "An error", responseContent = Some(problem.toJson.compactPrint))

      Get() ~> handleError("error message", headersFromContext)(contexts, logger)(Failure(error)) ~> check {
        status.intValue shouldBe error.code
        responseAs[Problem] shouldBe expectedProblem(StatusCodes.NotFound, problemError.code, problemError.detail)
        headers should contain allElementsOf headersFromContext
      }
    }

    "return generic error on unexpected error" in {
      val error = new Exception("I'm a generic exception")

      val message = "error message"

      Get() ~> handleError(message, headersFromContext)(contexts, logger)(Failure(error)) ~> check {
        status.intValue shouldBe 500
        responseAs[Problem] shouldBe problemOf(StatusCodes.InternalServerError, GenericError(message))
        headers should contain allElementsOf headersFromContext
      }
    }
  }
}
