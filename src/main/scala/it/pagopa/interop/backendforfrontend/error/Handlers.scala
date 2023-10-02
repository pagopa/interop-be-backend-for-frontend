package it.pagopa.interop.backendforfrontend.error

import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.server.{Route, StandardRoute}
import com.typesafe.scalalogging.LoggerTakingImplicit
import it.pagopa.interop.agreementprocess.client.invoker.{ApiError => AgreementProcessError}
import it.pagopa.interop.attributeregistryprocess.client.invoker.{ApiError => AttributeRegistryError}
import it.pagopa.interop.authorizationprocess.client.invoker.{ApiError => AuthorizationProcessError}
import it.pagopa.interop.backendforfrontend.api.impl.{problemFormat, problemOf, serviceCode}
import it.pagopa.interop.backendforfrontend.error.BFFErrors._
import it.pagopa.interop.backendforfrontend.model.{Problem, TokenGenerationValidationResult}
import it.pagopa.interop.catalogprocess.client.invoker.{ApiError => CatalogProcessError}
import it.pagopa.interop.commons.jwt.errors.InvalidJWTClaim
import it.pagopa.interop.commons.logging.ContextFieldsToLog
import it.pagopa.interop.commons.ratelimiter
import it.pagopa.interop.commons.ratelimiter.model.Headers
import it.pagopa.interop.commons.utils.errors.AkkaResponses._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.{GenericError, TooManyRequests}
import it.pagopa.interop.commons.utils.errors.{ComponentError, GenericComponentErrors, ServiceCode}
import it.pagopa.interop.purposeprocess.client.invoker.{ApiError => PurposeProcessError}
import it.pagopa.interop.selfcare.partyprocess.client.invoker.{ApiError => PartyProcessError}
import it.pagopa.interop.selfcare.userregistry.client.invoker.{ApiError => UserRegistryError}
import it.pagopa.interop.selfcare.v2.client.invoker.{ApiError => SelfcareV2Error}
import it.pagopa.interop.tenantprocess.client.invoker.{ApiError => TenantProcessError}
import spray.json._

import scala.util.{Failure, Success, Try}

object Handlers {

  def handleError(logMessage: String, headers: List[HttpHeader])(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(err: AuthorizationProcessError[_]) =>
      completeWithError(err.code, headers, err.responseContent, logMessage)
    case Failure(err: PurposeProcessError[_])   => completeWithError(err.code, headers, err.responseContent, logMessage)
    case Failure(err: AgreementProcessError[_]) => completeWithError(err.code, headers, err.responseContent, logMessage)
    case Failure(err: AttributeRegistryError[_]) =>
      completeWithError(err.code, headers, err.responseContent, logMessage)
    case Failure(err: CatalogProcessError[_]) => completeWithError(err.code, headers, err.responseContent, logMessage)
    case Failure(err: TenantProcessError[_])  => completeWithError(err.code, headers, err.responseContent, logMessage)
    case Failure(err: PartyProcessError[_])   => completeWithError(err.code, headers, err.responseContent, logMessage)
    case Failure(err: UserRegistryError[_])   => completeWithError(err.code, headers, err.responseContent, logMessage)
    case Failure(err: SelfcareV2Error[_])     => completeWithError(err.code, headers, err.responseContent, logMessage)
    case Failure(tmr: ratelimiter.error.Errors.TooManyRequests) =>
      tooManyRequests(
        GenericComponentErrors.TooManyRequests,
        s"Requests limit exceeded for organization ${tmr.tenantId}",
        Headers.headersFromStatus(tmr.status)
      )
    case Failure(err: InvalidJWTClaim)     => unauthorized(GenericError(err.getMessage()), logMessage, headers)
    case Failure(err: AttributeNotExists)  => internalServerError(err, logMessage, headers)
    case Failure(err: UnknownTenantOrigin) => badRequest(err, logMessage, headers)
    case Failure(err: InvalidInterfaceContentTypeDetected) => badRequest(err, logMessage, headers)
    case Failure(err: InvalidInterfaceFileDetected)        => badRequest(err, logMessage, headers)
    case Failure(err: InvalidEServiceRequester)            => forbidden(err, logMessage, headers)
    case Failure(err: AgreementDescriptorNotFound)         => notFound(err, logMessage, headers)
    case Failure(err: EServiceDescriptorNotFound)          => notFound(err, logMessage, headers)
    case Failure(err: EServiceRiskAnalysisNotFound)        => notFound(err, logMessage, headers)
    case Failure(err: PrivacyNoticeNotFound)               => notFound(err, logMessage, headers)
    case Failure(err)                                      => internalServerError(err, logMessage, headers)
  }

  def handleTokenValidationError(logMessage: String, headers: List[HttpHeader])(
    success: TokenGenerationValidationResult => Route
  )(
    result: Try[TokenGenerationValidationResult]
  )(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                            => success(s)
      case Failure(res: ClientAssertionValidationWrapper)        => success(res.result)
      case Failure(ex: OrganizationNotAllowed)                   => forbidden(ex, logMessage, headers)
      case Failure(ex: ratelimiter.error.Errors.TooManyRequests) =>
        tooManyRequests(
          TooManyRequests,
          s"Requests limit exceeded for organization ${ex.tenantId}",
          Headers.headersFromStatus(ex.status)
        )
      case Failure(ex)                                           => internalServerError(ex, logMessage, headers)
    }

  private def completeWithError[T](
    statusCode: Int,
    headers: List[HttpHeader],
    response: Option[T],
    endpointMessage: String
  )(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): StandardRoute = {
    val problem = response match {
      case Some(problemBody: String) => responseToProblem(problemBody, endpointMessage)
      case Some(problem: Problem)    => problem
      case Some(other)               => responseToProblem(other.toString, endpointMessage)
      case None                      => unexpectedErrorProblem(endpointMessage)
    }

    val (actualServiceCode, error): (ServiceCode, ComponentError) =
      problem.errors.headOption.map(_.code.split("-").toList) match {
        case Some(downstreamServiceCode :: downstreamErrorCode :: Nil) =>
          val errorMessage = problem.errors.map(_.detail).mkString(",")
          (ServiceCode(downstreamServiceCode), DownstreamError(downstreamErrorCode, errorMessage))
        case _                                                         => (serviceCode, GenericError(endpointMessage))
      }

    statusCode match {
      case 400 => badRequest(error, endpointMessage, headers)(contexts, logger, actualServiceCode)
      case 401 => unauthorized(error, endpointMessage, headers)(contexts, logger, actualServiceCode)
      case 403 => forbidden(error, endpointMessage, headers)(contexts, logger, actualServiceCode)
      case 404 => notFound(error, endpointMessage, headers)(contexts, logger, actualServiceCode)
      case 409 => conflict(error, endpointMessage, headers)(contexts, logger, actualServiceCode)
      case _   => internalServerError(error, endpointMessage, headers)(contexts, logger, actualServiceCode)
    }
  }

  private def responseToProblem(problemBody: String, defaultMessage: String): Problem =
    // Note: the body should actually be Problem of the below service, but having the same schema,
    //   for convenience we can convert it directly to our model
    Try(problemBody.parseJson.convertTo[Problem]).getOrElse(unexpectedErrorProblem(defaultMessage))

  private def unexpectedErrorProblem(message: String): Problem =
    problemOf(StatusCodes.InternalServerError, GenericError(message))

}
