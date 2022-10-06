package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.attributeregistrymanagement.client.model.Attribute
import it.pagopa.interop.backendforfrontend.api.TenantsApiService
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model.{
  CertifiedAttributesResponse,
  DeclaredTenantAttributeSeed,
  Problem,
  VerifiedTenantAttributeSeed
}
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.AttributeConverter
import it.pagopa.interop.backendforfrontend.service.types.TenantProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.{
  AttributeRegistryManagementService,
  TenantManagementService,
  TenantProcessService
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.tenantmanagement.client.model.CertifiedTenantAttribute

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class TenantsApiServiceImpl(
  attributeRegistryService: AttributeRegistryManagementService,
  tenantManagementService: TenantManagementService,
  tenantProcessService: TenantProcessService
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

  override def addDeclaredAttribute(
    seed: DeclaredTenantAttributeSeed
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    val result: Future[Unit] = tenantProcessService.addDeclaredAttribute(seed.toSeed).void

    onComplete(result) {
      handleError(s"Error adding declared attribute ${seed.id} to requester tenant") orElse { case Success(_) =>
        addDeclaredAttribute204
      }
    }
  }

  override def revokeDeclaredAttribute(
    attributeId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    val result: Future[Unit] =
      for {
        attributeUuid <- attributeId.toFutureUUID
        _             <- tenantProcessService.revokeDeclaredAttribute(attributeUuid).void
      } yield ()

    onComplete(result) {
      handleError(s"Error revoking declared attribute $attributeId to requester tenant") orElse { case Success(_) =>
        revokeDeclaredAttribute204
      }
    }
  }

  override def verifyVerifiedAttribute(tenantId: String, seed: VerifiedTenantAttributeSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Unit] = for {
      tenantUuid <- tenantId.toFutureUUID
      _          <- tenantProcessService.verifyVerifiedAttribute(tenantUuid, seed.toSeed).void
    } yield ()

    onComplete(result) {
      handleError(s"Error verifying verified attribute ${seed.id} to tenant $tenantId") orElse { case Success(_) =>
        verifyVerifiedAttribute204
      }
    }
  }

  override def revokeVerifiedAttribute(tenantId: String, attributeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Unit] =
      for {
        tenantUuid    <- tenantId.toFutureUUID
        attributeUuid <- attributeId.toFutureUUID
        _             <- tenantProcessService.revokeVerifiedAttribute(tenantUuid, attributeUuid).void
      } yield ()

    onComplete(result) {
      handleError(s"Error revoking verified attribute $attributeId to tenant $tenantId") orElse { case Success(_) =>
        revokeDeclaredAttribute204
      }
    }
  }
}
