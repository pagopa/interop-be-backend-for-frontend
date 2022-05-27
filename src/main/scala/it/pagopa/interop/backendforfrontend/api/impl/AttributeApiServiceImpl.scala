package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.backendforfrontend.api.AttributesApiService
import it.pagopa.interop.backendforfrontend.model.{Attribute, AttributesResponse, Problem}
import it.pagopa.interop.backendforfrontend.service.AttributeRegistryManagementService
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.ResourceNotFoundError

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

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
      case Success(attributes) =>
        getAttributes200(attributes)
      case Failure(_)          =>
        logger.error(s"Error while getting attributes for search string $search")
        getAttributes404(
          problemOf(StatusCodes.NotFound, ResourceNotFoundError(s"Attributes containing string $search"))
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
      case Success(attribute) =>
        getAttributeByOriginAndCode200(attribute)
      case Failure(_)         =>
        logger.error(s"Error while getting attribute with origin = $origin and code = $code")
        getAttributeByOriginAndCode404(
          problemOf(
            StatusCodes.NotFound,
            ResourceNotFoundError(s"Attribute with origin = $origin and code = $code not found")
          )
        )
    }
  }
}
