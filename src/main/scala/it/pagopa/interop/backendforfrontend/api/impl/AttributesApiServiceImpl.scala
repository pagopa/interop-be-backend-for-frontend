package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.AttributesApiService
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model.{Attribute, AttributeSeed, AttributesResponse, Problem}
import it.pagopa.interop.backendforfrontend.service.AttributeRegistryManagementService
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class AttributesApiServiceImpl(attributeRegistryManagementApiService: AttributeRegistryManagementService)(
  implicit ec: ExecutionContext
) extends AttributesApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getAttributes(search: Option[String])(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[AttributesResponse] =
      attributeRegistryManagementApiService.getAttributes(search)(contexts).map(_.toResponse)

    onComplete(result) {
      handleError(s"Error retrieving attributes for search string $search") orElse { case Success(attributes) =>
        getAttributes200(attributes)
      }
    }
  }

  override def getAttributeById(attributeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Attribute] = for {
      attributeUuid <- attributeId.toFutureUUID
      response      <- attributeRegistryManagementApiService.getAttributeById(attributeUuid)(contexts)
      converted = response.toAttribute
    } yield converted

    onComplete(result) {
      handleError(s"Error retrieving attribute with id $attributeId") orElse { case Success(attribute) =>
        getAttributeById200(attribute)
      }
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
      handleError(s"Error retrieving attribute with origin = $origin and code = $code") orElse {
        case Success(attribute) => getAttributeByOriginAndCode200(attribute)
      }
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
      handleError(s"Error creating attribute with seed $attributeSeed") orElse { case Success(attribute) =>
        createAttribute201(attribute)
      }
    }
  }

}
