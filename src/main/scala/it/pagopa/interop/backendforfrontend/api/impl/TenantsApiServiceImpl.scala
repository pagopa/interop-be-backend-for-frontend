package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.attributeregistrymanagement.client.model.Attribute
import it.pagopa.interop.backendforfrontend.api.TenantsApiService
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model.{CertifiedAttributesResponse, Problem}
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.AttributeConverter
import it.pagopa.interop.backendforfrontend.service.{AttributeRegistryManagementService, TenantManagementService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.tenantmanagement.client.model.CertifiedTenantAttribute

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class TenantsApiServiceImpl(
  attributeRegistryService: AttributeRegistryManagementService,
  tenantManagementService: TenantManagementService
)(implicit ec: ExecutionContext)
    extends TenantsApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getCertifiedAttributes(tenantId: String)(implicit
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
      tenantUUID <- tenantId.toFutureUUID
      tenant     <- tenantManagementService.getTenant(tenantUUID)
      attributes <- Future.traverse(tenant.attributes.mapFilter(_.certified))(getAttributeSafe).map(_.flatten)
    } yield CertifiedAttributesResponse(attributes.map(_.toCertifiedAttribute))

    onComplete(result) {
      handleError(s"Error retrieving certified attributes for tenant $tenantId") orElse { case Success(attributes) =>
        getCertifiedAttributes200(attributes)
      }
    }
  }

}
