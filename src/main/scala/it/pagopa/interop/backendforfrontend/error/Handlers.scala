package it.pagopa.interop.backendforfrontend.error

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.StandardRoute
import com.typesafe.scalalogging.LoggerTakingImplicit
import it.pagopa.interop.agreementprocess.client.invoker.{ApiError => AgreementProcessError}
import it.pagopa.interop.attributeregistrymanagement.client.invoker.{ApiError => AttributeRegistryError}
import it.pagopa.interop.backendforfrontend.api.impl.{problemFormat, problemOf}
import it.pagopa.interop.backendforfrontend.model.Problem
import it.pagopa.interop.catalogmanagement.client.invoker.{ApiError => CatalogManagementError}
import it.pagopa.interop.commons.logging.ContextFieldsToLog
import it.pagopa.interop.commons.ratelimiter
import it.pagopa.interop.commons.ratelimiter.model.{Headers, RateLimitStatus}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.{GenericError, TooManyRequests}
import it.pagopa.interop.selfcare.partyprocess.client.invoker.{ApiError => PartyProcessError}
import it.pagopa.interop.selfcare.userregistry.client.invoker.{ApiError => UserRegistryError}
import it.pagopa.interop.tenantmanagement.client.invoker.{ApiError => TenantManagementError}
import it.pagopa.interop.tenantprocess.client.invoker.{ApiError => TenantProcessError}
import spray.json._

import scala.util.{Failure, Try}

object Handlers {

  def handleError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): PartialFunction[Try[_], StandardRoute] = log(logMessage) andThen {
    case Failure(err: AgreementProcessError[_])                 => completeWithError(err.responseContent, logMessage)
    case Failure(err: AttributeRegistryError[_])                => completeWithError(err.responseContent, logMessage)
    case Failure(err: CatalogManagementError[_])                => completeWithError(err.responseContent, logMessage)
    case Failure(err: TenantManagementError[_])                 => completeWithError(err.responseContent, logMessage)
    case Failure(err: TenantProcessError[_])                    => completeWithError(err.responseContent, logMessage)
    case Failure(err: PartyProcessError[_])                     => completeWithError(err.responseContent, logMessage)
    case Failure(err: UserRegistryError[_])                     => completeWithError(err.responseContent, logMessage)
    case Failure(tmr: ratelimiter.error.Errors.TooManyRequests) => tooManyRequests(tmr.status)
    case Failure(_)                                             => internalServerError(logMessage)
  }

  private def log(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], Try[_]] = {
    case res @ Failure(ex) =>
      logger.error(logMessage, ex)
      res
    case other             => other
  }

  private def completeWithError[T](response: Option[T], defaultMessage: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): StandardRoute = {
    val problem = response match {
      case Some(problemBody: String) => responseToProblem(problemBody, defaultMessage)
      case Some(problem: Problem)    => problem
      case Some(other)               => responseToProblem(other.toString, defaultMessage)
      case None                      => unexpectedErrorProblem(defaultMessage)
    }
    complete(problem.status, problem)
  }

  private def tooManyRequests(
    rateLimitStatus: RateLimitStatus
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): StandardRoute =
    complete(
      StatusCodes.TooManyRequests,
      Headers.headersFromStatus(rateLimitStatus),
      problemOf(StatusCodes.TooManyRequests, TooManyRequests)
    )

  private def responseToProblem(problemBody: String, defaultMessage: String): Problem =
    // Note: the body should actually be Problem of the below service, but having the same schema,
    //   for convenience we can convert it directly to our model
    Try(problemBody.parseJson.convertTo[Problem]).getOrElse(unexpectedErrorProblem(defaultMessage))

  private def internalServerError(
    errorMessage: String
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): StandardRoute = {
    val problem = unexpectedErrorProblem(errorMessage)
    complete(problem.status, problem)
  }

  private def unexpectedErrorProblem(message: String): Problem =
    problemOf(StatusCodes.InternalServerError, GenericError(message))

}
