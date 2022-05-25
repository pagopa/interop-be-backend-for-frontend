package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.PartyApiService
import it.pagopa.interop.backendforfrontend.api.impl.converters.PartyProcessConverter
import it.pagopa.interop.backendforfrontend.error.BFFErrors.RelationshipNotFound
import it.pagopa.interop.backendforfrontend.model.{Problem, RelationshipInfo}
import it.pagopa.interop.backendforfrontend.service.{PartyProcessService, UserRegistryService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.OpenapiUtils._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.GenericError
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.ResourceNotFoundError
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class PartyApiServiceImpl(
  partyProcessService: PartyProcessService,
  userRegistryService: UserRegistryService
)(implicit ec: ExecutionContext)
    extends PartyApiService {

  private val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](LoggerFactory.getLogger(this.getClass))

  /**
   * Code: 200, Message: successful operation, DataType: RelationshipInfo
   * Code: 404, Message: Not found, DataType: Problem
   */
  override def getRelationship(relationshipId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerRelationshipInfo: ToEntityMarshaller[RelationshipInfo],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Retrieving relationship $relationshipId")
    val result: Future[RelationshipInfo] = for {
      uuid             <- relationshipId.toFutureUUID
      relationship     <- partyProcessService.getRelationship(uuid)
      user             <- userRegistryService.findById(relationship.from)
      relationshipInfo <- PartyProcessConverter.toApiRelationshipInfo(user, relationship)
    } yield relationshipInfo

    onComplete(result) {
      case Success(relationshipInfo)          => getRelationship200(relationshipInfo)
      case Failure(ex: ResourceNotFoundError) =>
        logger.error(s"Error while retrieving relationship $relationshipId - ${ex.getMessage}")
        getRelationship404(problemOf(StatusCodes.NotFound, RelationshipNotFound(relationshipId)))
      case Failure(ex)                        =>
        logger.error(s"Error while retrieving relationship $relationshipId - ${ex.getMessage}")
        complete(
          StatusCodes.InternalServerError,
          problemOf(
            StatusCodes.InternalServerError,
            GenericError(s"Something went wrong trying to get relationship $relationshipId - ${ex.getMessage}")
          )
        )
    }

  }

  /**
   * Code: 200, Message: successful operation, DataType: Seq[RelationshipInfo]
   */
  override def getUserInstitutionRelationships(
    personId: Option[String],
    roles: String,
    states: String,
    products: String,
    productRoles: String,
    institutionId: String
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerRelationshipInfoarray: ToEntityMarshaller[Seq[RelationshipInfo]]
  ): Route = {
    logger.info(s"Retrieving relationships for institutions $institutionId")

    val result: Future[Seq[RelationshipInfo]] = for {
      institutionIdUUID <- institutionId.toFutureUUID
      personIdUUID      <- personId.traverse(_.toFutureUUID)
      rolesParams       <- parseArrayParameters(roles).traverse(PartyProcessConverter.toPartyRole).toFuture
      statesParams      <- parseArrayParameters(states).traverse(PartyProcessConverter.toRelationshipState).toFuture
      productsParams     = parseArrayParameters(products)
      productRolesParams = parseArrayParameters(productRoles)
      relationships     <- partyProcessService.getUserInstitutionRelationships(
        institutionIdUUID,
        personIdUUID,
        rolesParams,
        statesParams,
        productsParams,
        productRolesParams
      )
      relationshipsInfo <- relationships.traverse { relationship =>
        for {
          user             <- userRegistryService.findById(relationship.from)
          relationshipInfo <- PartyProcessConverter.toApiRelationshipInfo(user, relationship)
        } yield relationshipInfo
      }
    } yield relationshipsInfo

    onComplete(result) {
      case Success(relationshipsInfo) => getUserInstitutionRelationships200(relationshipsInfo)
      case Failure(ex)                =>
        logger.error(s"Error while retrieving relationships for institutions $institutionId - ${ex.getMessage}")
        complete(
          StatusCodes.InternalServerError,
          problemOf(
            StatusCodes.InternalServerError,
            GenericError(
              s"Something went wrong trying to get relationship info for institution $institutionId - ${ex.getMessage}"
            )
          )
        )
    }

  }
}