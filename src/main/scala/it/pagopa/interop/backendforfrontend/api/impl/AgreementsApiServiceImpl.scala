package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.{Route, StandardRoute}
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.backendforfrontend.api.AgreementsApiService
import it.pagopa.interop.backendforfrontend.model.{AgreementPayload, CreatedResource, Problem}
import it.pagopa.interop.backendforfrontend.service.AgreementProcessService
import it.pagopa.interop.backendforfrontend.service.types.AgreementProcessServiceTypes.AgreementPayloadConverter
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.GenericError

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class AgreementsApiServiceImpl(agreementProcessService: AgreementProcessService)(implicit
  ec: ExecutionContext
) extends AgreementsApiService {

  private val logger = Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def createAgreement(payload: AgreementPayload)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {
    val result: Future[CreatedResource] = for {
      result <- agreementProcessService.createAgreement(payload.toSeed)
    } yield CreatedResource(result.id)

    onComplete(result) {
      case Success(resource) =>
        createAgreement200(resource)
      case Failure(e)        =>
        val message =
          s"Error while creating agreement for EService ${payload.eserviceId} and Descriptor ${payload.descriptorId}"
        logger.error(message, e)
        internalServerError(message)
    }
  }

  private def internalServerError(
    errorMessage: String
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): StandardRoute = {
    val statusCode = StatusCodes.InternalServerError
    complete(statusCode.intValue, problemOf(statusCode, GenericError(errorMessage)))
  }

}
