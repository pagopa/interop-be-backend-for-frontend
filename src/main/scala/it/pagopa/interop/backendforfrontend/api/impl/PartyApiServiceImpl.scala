package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.PartyApiService
import it.pagopa.interop.backendforfrontend.api.impl.converters.PartyProcessConverter
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.BFFErrors._
import it.pagopa.interop.backendforfrontend.common.HeaderUtils._
import it.pagopa.interop.backendforfrontend.model.{Problem, RelationshipInfo}
import it.pagopa.interop.backendforfrontend.service.{
  AttributeRegistryProcessService,
  PartyProcessService,
  TenantProcessService,
  UserRegistryService
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.OpenapiUtils._
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.{GenericError, ResourceNotFoundError}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class PartyApiServiceImpl(
  partyProcessService: PartyProcessService,
  userRegistryService: UserRegistryService,
  attributeRegistryService: AttributeRegistryProcessService,
  tenantProcessService: TenantProcessService
)(implicit ec: ExecutionContext)
    extends PartyApiService {

  private val logger: LoggerTakingImplicit[ContextFieldsToLog]                                                     =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getRelationship(relationshipId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerRelationshipInfo: ToEntityMarshaller[RelationshipInfo],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Retrieving relationship $relationshipId")
    val headers: List[HttpHeader] = headersFromContext()

    val result: Future[RelationshipInfo] = for {
      uuid             <- relationshipId.toFutureUUID
      relationship     <- partyProcessService.getRelationship(uuid)
      user             <- userRegistryService.findById(relationship.from)
      relationshipInfo <- PartyProcessConverter.toApiRelationshipInfo(user, relationship)
    } yield relationshipInfo

    onComplete(result) {
      case Success(relationshipInfo)          => getRelationship200(headers)(relationshipInfo)
      case Failure(ex: ResourceNotFoundError) =>
        logger.error(s"Error while retrieving relationship $relationshipId - ${ex.getMessage}")
        getRelationship404(headers)(problemOf(StatusCodes.NotFound, RelationshipNotFound(relationshipId)))
      case Failure(ex)                        =>
        logger.error(s"Error while retrieving relationship $relationshipId - ${ex.getMessage}")
        complete(
          StatusCodes.InternalServerError,
          headers,
          problemOf(
            StatusCodes.InternalServerError,
            GenericError(s"Something went wrong trying to get relationship $relationshipId - ${ex.getMessage}")
          )
        )
    }
  }
  override def getUserInstitutionRelationships(
    personId: Option[String],
    roles: String,
    states: String,
    productRoles: String,
    query: Option[String],
    tenantId: String
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerRelationshipInfoarray: ToEntityMarshaller[Seq[RelationshipInfo]]
  ): Route = {
    logger.info(s"Retrieving relationships for institutions $tenantId")
    val headers: List[HttpHeader] = headersFromContext()

    val result: Future[Seq[RelationshipInfo]] = for {
      personIdUUID <- personId.traverse(_.toFutureUUID)
      rolesParams  <- parseArrayParameters(roles).traverse(PartyProcessConverter.toPartyRole).toFuture
      statesParams <- parseArrayParameters(states).traverse(PartyProcessConverter.toRelationshipState).toFuture
      productRolesParams = parseArrayParameters(productRoles)
      tenant            <- tenantId.toFutureUUID >>= tenantProcessService.getTenant
      selfcareId        <- tenant.selfcareId.toFuture(MissingSelfcareId(tenant.id))
      relationships     <- partyProcessService.getUserInstitutionRelationships(
        selfcareId,
        personIdUUID,
        rolesParams,
        statesParams,
        List(ApplicationConfiguration.selfcareProductId),
        productRolesParams
      )
      relationshipsInfo <- Future.traverse(relationships) { relationship =>
        for {
          user             <- userRegistryService.findById(relationship.from)
          relationshipInfo <- PartyProcessConverter.toApiRelationshipInfo(user, relationship)
        } yield relationshipInfo
      }
    } yield filterByUserName(relationshipsInfo, query)

    onComplete(result) {
      case Success(relationshipsInfo) => getUserInstitutionRelationships200(headers)(relationshipsInfo)
      case Failure(ex)                =>
        logger.error(
          s"Error while retrieving relationships for institutions corresponding to tenant $tenantId - ${ex.getMessage}"
        )
        complete(
          StatusCodes.InternalServerError,
          headers,
          problemOf(
            StatusCodes.InternalServerError,
            GenericError(
              s"Something went wrong trying to get relationship info for institution corresponding to tenant $tenantId - ${ex.getMessage}"
            )
          )
        )
    }
  }
  private def filterByUserName(relationships: Seq[RelationshipInfo], query: Option[String]): Seq[RelationshipInfo] = {
    query.fold(relationships)(q =>
      relationships.filter(relationship =>
        relationship.name.toLowerCase.contains(q.toLowerCase) || relationship.familyName.toLowerCase.contains(
          q.toLowerCase
        )
      )
    )
  }
}
