package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.AttributesApiService
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes._
import it.pagopa.interop.backendforfrontend.service.{
  AttributeRegistryManagementService,
  AttributeRegistryProcessService
}

import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class AttributesApiServiceImpl(
  attributeRegistryManagementApiService: AttributeRegistryManagementService,
  attributeRegistryProcessApiService: AttributeRegistryProcessService
)(implicit ec: ExecutionContext)
    extends AttributesApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

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
    val result: Future[Attribute] =
      attributeRegistryManagementApiService.getAttributeByOriginAndCode(origin, code)(contexts).map(_.toAttribute)

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
    val result: Future[Attribute] =
      attributeRegistryManagementApiService.createAttribute(attributeSeed.toSeed).map(_.toAttribute)

    onComplete(result) {
      handleError(s"Error creating attribute with seed $attributeSeed") orElse { case Success(attribute) =>
        createAttribute201(attribute)
      }
    }
  }

  override def getAttributes(q: Option[String], limit: Int, offset: Int, kinds: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttributes: ToEntityMarshaller[Attributes]
  ): Route = {
    val result: Future[Attributes] =
      attributeRegistryProcessApiService
        .getAttributes(
          q,
          limit,
          offset,
          parseArrayParameters(kinds)
            .map(AttributeKind.fromValue)
            .flatMap(_.map(_.toProcess).toOption)
        )
        .map(a => Attributes(Pagination(offset, limit, a.totalCount), a.results.map(_.toApi)))

    onComplete(result) {
      handleError(
        s"Error retrieving attributes with name = $q, limit = $limit, offset = $offset, kinds = $kinds"
      ) orElse { case Success(attributes) =>
        getAttributes200(attributes)
      }
    }
  }
}
