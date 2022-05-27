package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.backendforfrontend.api.AttributesApiService
import it.pagopa.interop.backendforfrontend.model.{AttributesResponse, Problem}
import it.pagopa.interop.backendforfrontend.service.AttributeRegistryManagementService
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.Converter
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.ResourceNotFoundError

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class AttributesApiServiceImpl(attributeRegistryManagementApiService: AttributeRegistryManagementService)(
  implicit ec: ExecutionContext
) extends AttributesApiService {

  private val logger = Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  /**
   * Code: 200, Message: array of currently available attributes, DataType: AttributesResponse
   * Code: 404, Message: Attributes not found, DataType: Problem
   */
  override def getAttributes(search: Option[String])(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[AttributesResponse] = attributeRegistryManagementApiService.getAttributes(search)(contexts).map(_.toResponse)

    onComplete(result) {
      case Success(attributes) =>
        getAttributes200(attributes)
      case Failure(_)          =>
        logger.error(s"Error while getting attributes for search string $search")
        getAttributes404(
          problemOf(StatusCodes.NotFound, ResourceNotFoundError(s"Attributes containing string $search"))
        )
    }

  }
}
