package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.backendforfrontend.api.AttributesApiService
import it.pagopa.interop.backendforfrontend.model.{Attribute, AttributesResponse, Problem}
import it.pagopa.interop.backendforfrontend.service.AttributeRegistryManagementService
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.{
  GenericError,
  ResourceNotFoundError,
  ResourceConflictError
}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import it.pagopa.interop.backendforfrontend.model.AttributeSeed

final case class AttributesApiServiceImpl(attributeRegistryManagementApiService: AttributeRegistryManagementService)(
  implicit ec: ExecutionContext
) extends AttributesApiService {

  private val logger = Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getAttributes(search: Option[String])(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[AttributesResponse] =
      attributeRegistryManagementApiService.getAttributes(search)(contexts).map(_.toResponse)

    onComplete(result) {
      case Success(attributes)                =>
        getAttributes200(attributes)
      case Failure(ex: ResourceNotFoundError) =>
        logger.error(s"Error while getting attributes for search string $search - ${ex.getMessage}")
        getAttributes404(
          problemOf(StatusCodes.NotFound, ResourceNotFoundError(s"Attributes containing string $search"))
        )
      case Failure(ex)                        =>
        logger.error(s"Error while getting attributes for search string $search - ${ex.getMessage}")
        complete(
          StatusCodes.InternalServerError,
          problemOf(
            StatusCodes.InternalServerError,
            GenericError(
              s"Something went wrong trying to search attributes containing string $search - ${ex.getMessage}"
            )
          )
        )
    }
  }

  override def getAttributeByOriginAndCode(origin: String, code: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Attribute] = for {
      response <- attributeRegistryManagementApiService.getAttributeByOriginAndCode(origin, code)(contexts)
      converted = response.toAttribute
    } yield converted

    onComplete(result) {
      case Success(attribute)                 =>
        getAttributeByOriginAndCode200(attribute)
      case Failure(ex: ResourceNotFoundError) =>
        logger.error(s"Error while getting attribute with origin = $origin and code = $code - ${ex.getMessage}")
        getAttributes404(
          problemOf(
            StatusCodes.NotFound,
            ResourceNotFoundError(s"Attribute with origin = $origin and code = $code not found")
          )
        )
      case Failure(ex)                        =>
        logger.error(s"Error while getting attribute with origin = $origin and code = $code - ${ex.getMessage}")
        complete(
          StatusCodes.InternalServerError,
          problemOf(
            StatusCodes.InternalServerError,
            GenericError(
              s"Something went wrong trying to get attribute with origin = $origin and code = $code - ${ex.getMessage}"
            )
          )
        )
    }
  }

  override def createAttribute(attributeSeed: AttributeSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Attribute] = for {
      result <- attributeRegistryManagementApiService.createAttribute(attributeSeed.toSeed)
    } yield result.toAttribute

    onComplete(result) {
      case Success(attribute)                =>
        createAttribute201(attribute)
      case Failure(e: ResourceConflictError) =>
        val errorMessage: String = s"Attribute with name ${e.resourceId} already existing"
        logger.error(s"Error while creating attribute with seed $attributeSeed - $errorMessage")
        createAttribute409(problemOf(StatusCodes.Conflict, ResourceConflictError(errorMessage)))
      case Failure(e)                        =>
        logger.error(s"Error while creating attribute with seed $attributeSeed", e)
        complete(
          StatusCodes.InternalServerError,
          problemOf(
            StatusCodes.InternalServerError,
            GenericError(
              s"Something went wrong trying to create an attribute with seed $attributeSeed - ${e.getMessage}"
            )
          )
        )
    }
  }

}
