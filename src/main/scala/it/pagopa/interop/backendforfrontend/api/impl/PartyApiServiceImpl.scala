package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.selfcare.partyprocess.client.model.{Attribute => PartyAttribute}
import it.pagopa.interop.backendforfrontend.api.PartyApiService
import it.pagopa.interop.backendforfrontend.api.impl.converters.PartyProcessConverter
import it.pagopa.interop.backendforfrontend.error.BFFErrors.{InstitutionNotFound, RelationshipNotFound}
import it.pagopa.interop.backendforfrontend.model.{Institution, Problem, RelationshipInfo}
import it.pagopa.interop.backendforfrontend.service.{PartyProcessService, UserRegistryService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.OpenapiUtils._
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.{GenericError, ResourceNotFoundError}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import it.pagopa.interop.backendforfrontend.service.AttributeRegistryManagementService
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.AttributeConverter
import it.pagopa.interop.backendforfrontend.model.CertifiedAttributesResponse
import it.pagopa.interop.attributeregistrymanagement.client.model.AttributeKind
import it.pagopa.interop.attributeregistrymanagement.client.model.Attribute

final case class PartyApiServiceImpl(
  partyProcessService: PartyProcessService,
  userRegistryService: UserRegistryService,
  attributeRegistryService: AttributeRegistryManagementService
)(implicit ec: ExecutionContext)
    extends PartyApiService {

  private val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

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
    query: Option[String],
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
      relationshipsInfo <- Future.traverse(relationships) { relationship =>
        for {
          user             <- userRegistryService.findById(relationship.from)
          relationshipInfo <- PartyProcessConverter.toApiRelationshipInfo(user, relationship)
        } yield relationshipInfo
      }
    } yield filterByUserName(relationshipsInfo, query)

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

  private def filterByUserName(relationships: Seq[RelationshipInfo], query: Option[String]): Seq[RelationshipInfo] = {
    query.fold(relationships)(q =>
      relationships.filter(relationship =>
        relationship.name.toLowerCase.contains(q.toLowerCase) || relationship.familyName.toLowerCase.contains(
          q.toLowerCase
        )
      )
    )
  }

  /**
   * Code: 200, Message: successful operation, DataType: Institution
   * Code: 400, Message: Invalid id supplied, DataType: Problem
   * Code: 404, Message: Not found, DataType: Problem
   */
  override def getInstitution(institutionId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerInstitution: ToEntityMarshaller[Institution],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Retrieving institution $institutionId")
    val result: Future[Institution] = for {
      uuid        <- institutionId.toFutureUUID
      institution <- partyProcessService.getInstitution(uuid)
    } yield PartyProcessConverter.toApiInstitution(institution)

    onComplete(result) {
      case Success(institution)               => getInstitution200(institution)
      case Failure(ex: ResourceNotFoundError) =>
        logger.error(s"Error while retrieving institution $institutionId - ${ex.getMessage}")
        getInstitution404(problemOf(StatusCodes.NotFound, InstitutionNotFound(institutionId)))
      case Failure(ex)                        =>
        logger.error(s"Error while retrieving institution $institutionId - ${ex.getMessage}")
        complete(
          StatusCodes.InternalServerError,
          problemOf(
            StatusCodes.InternalServerError,
            GenericError(s"Something went wrong trying to get institution $institutionId - ${ex.getMessage}")
          )
        )
    }
  }

  override def getCertifiedAttributes(institutionId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCertifiedAttributesResponse: ToEntityMarshaller[CertifiedAttributesResponse]
  ): Route = {

    def getAttributeSafe(partyAttribute: PartyAttribute): Future[Option[Attribute]] = attributeRegistryService
      .getAttributeByOriginAndCode(partyAttribute.origin, partyAttribute.code)
      .redeem(
        e => {
          logger.error(s"Unable to find attribute ${partyAttribute.origin}/${partyAttribute.code}", e)
          Option.empty[Attribute]
        },
        Option(_)
      )

    val result: Future[CertifiedAttributesResponse] = for {
      institutionUUID <- institutionId.toFutureUUID
      institution     <- partyProcessService.getInstitution(institutionUUID)
      attributes      <- Future.traverse(institution.attributes)(getAttributeSafe).map(_.flatten)
      certifiedAttributes = attributes.filter(_.kind == AttributeKind.CERTIFIED)
    } yield CertifiedAttributesResponse(certifiedAttributes.map(_.toCertifiedAttribute))

    onComplete(result) {
      case Success(attributes)               =>
        getCertifiedAttributes200(attributes)
      case Failure(e: ResourceNotFoundError) =>
        logger.error(s"Error while retrieving  certified attributes for $institutionId", e)
        getInstitution404(problemOf(StatusCodes.NotFound, e))
      case Failure(e)                        =>
        logger.error(s"Error while retrieving certified attributes for $institutionId", e)
        complete(
          StatusCodes.InternalServerError,
          problemOf(
            StatusCodes.InternalServerError,
            GenericError(s"Error while retrieving certified attributes for $institutionId - ${e.getMessage}")
          )
        )
    }
  }

}
