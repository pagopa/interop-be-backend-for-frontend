package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.attributeregistrymanagement.client.model.Attribute
import it.pagopa.interop.backendforfrontend.api.TenantsApiService
import it.pagopa.interop.backendforfrontend.model.{CertifiedAttributesResponse, Problem}
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.AttributeConverter
import it.pagopa.interop.backendforfrontend.service.{AttributeRegistryManagementService, TenantManagementService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.{GenericError, ResourceNotFoundError}
import it.pagopa.interop.tenantmanagement.client.model.CertifiedTenantAttribute

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class TenantsApiServiceImpl(
  attributeRegistryService: AttributeRegistryManagementService,
  tenantManagementService: TenantManagementService
)(implicit ec: ExecutionContext)
    extends TenantsApiService {

  private val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getCertifiedAttributes(institutionId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCertifiedAttributesResponse: ToEntityMarshaller[CertifiedAttributesResponse]
  ): Route = {

    def getAttributeSafe(attribute: CertifiedTenantAttribute): Future[Option[Attribute]] =
      attributeRegistryService
        .getAttributeById(attribute.id)
        .redeem(
          e => {
            logger.error(s"Unable to find attribute ${attribute.id}", e)
            Option.empty[Attribute]
          },
          Option(_)
        )

    val result: Future[CertifiedAttributesResponse] = for {
      institutionUUID <- institutionId.toFutureUUID
      tenant          <- tenantManagementService.getTenant(institutionUUID)
      attributes      <- Future.traverse(tenant.attributes.mapFilter(_.certified))(getAttributeSafe).map(_.flatten)
    } yield CertifiedAttributesResponse(attributes.map(_.toCertifiedAttribute))

    onComplete(result) {
      case Success(attributes)               =>
        getCertifiedAttributes200(attributes)
      case Failure(e: ResourceNotFoundError) =>
        logger.error(s"Error while retrieving certified attributes for $institutionId", e)
        getCertifiedAttributes404(problemOf(StatusCodes.NotFound, e))
      case Failure(e)                        =>
        logger.error(s"Error while retrieving certified attributes for $institutionId", e)
        complete(
          StatusCodes.InternalServerError,
          problemOf(
            StatusCodes.InternalServerError,
            GenericError(s"Error while retrieving certified attributes for $institutionId")
          )
        )
    }
  }

}
