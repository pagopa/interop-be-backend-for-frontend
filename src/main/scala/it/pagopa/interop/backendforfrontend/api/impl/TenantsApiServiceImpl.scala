package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.TenantsApiService
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service.types.TenantManagementServiceTypes.AdaptableTenantAttribute._
import it.pagopa.interop.backendforfrontend.service.types.TenantManagementServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.TenantProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.{
  AttributeRegistryManagementService,
  TenantManagementService,
  TenantProcessService
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.tenantmanagement.client.model.{TenantAttribute => DepTenantAttribute}

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

  override def getProducers(name: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCompactOrganization: ToEntityMarshaller[CompactOrganizations]
  ): Route = {
    val result: Future[CompactOrganizations] =
      for {
        pagedResults <- tenantProcessService.getProducers(name = name, offset = offset, limit = limit)
      } yield CompactOrganizations(
        results = pagedResults.results.map(t => CompactOrganization(id = t.id, name = t.name)),
        pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
      )

    onComplete(result) {
      handleError(s"Error retrieving producers for name $name, offset $offset, limit $limit") orElse {
        case Success(r) => getProducers200(r)
      }
    }
  }

  override def getConsumers(name: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCompactOrganization: ToEntityMarshaller[CompactOrganizations]
  ): Route = {
    val result: Future[CompactOrganizations] =
      for {
        pagedResults <- tenantProcessService.getConsumers(name = name, offset = offset, limit = limit)
      } yield CompactOrganizations(
        results = pagedResults.results.map(t => CompactOrganization(id = t.id, name = t.name)),
        pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
      )

    onComplete(result) {
      handleError(s"Error retrieving consumers for name $name, offset $offset, limit $limit") orElse {
        case Success(r) => getConsumers200(r)
      }
    }
  }

  override def getCertifiedAttributes(tenantId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCertifiedAttributesResponse: ToEntityMarshaller[CertifiedAttributesResponse]
  ): Route =
    onComplete(getTenantAttributes(tenantId, _.certified)) {
      handleError(s"Error retrieving certified attributes for tenant $tenantId") orElse { case Success(attributes) =>
        getCertifiedAttributes200(CertifiedAttributesResponse(attributes))
      }
    }

  override def getVerifiedAttributes(tenantId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerVerifiedAttributesResponse: ToEntityMarshaller[VerifiedAttributesResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route =
    onComplete(getTenantAttributes(tenantId, _.verified)) {
      handleError(s"Error retrieving verified attributes for tenant $tenantId") orElse { case Success(attributes) =>
        getVerifiedAttributes200(VerifiedAttributesResponse(attributes))
      }
    }

  override def getDeclaredAttributes(tenantId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerDeclaredAttributesResponse: ToEntityMarshaller[DeclaredAttributesResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route =
    onComplete(getTenantAttributes(tenantId, _.declared)) {
      handleError(s"Error retrieving declared attributes for tenant $tenantId") orElse { case Success(attributes) =>
        getDeclaredAttributes200(DeclaredAttributesResponse(attributes))
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

  override def updateTenant(tenantId: String, tenantDelta: TenantDelta)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Unit] = for {
      tenantUUID <- tenantId.toFutureUUID
      tenant     <- tenantProcessService.getTenant(tenantUUID)
      ()         <- tenantProcessService.updateTenant(tenantUUID, tenantDelta.toExternalModel(tenant))
    } yield ()

    onComplete(result) {
      handleError(s"Error updating tenant with id $tenantId") orElse { case Success(_) =>
        updateTenant204
      }
    }
  }

  private def getTenantAttributes[DepAttribute, ApiAttribute](
    tenantId: String,
    attributeFromTenantAttribute: DepTenantAttribute => Option[DepAttribute]
  )(implicit
    contexts: Seq[(String, String)],
    adaptable: AdaptableTenantAttribute[DepAttribute, ApiAttribute]
  ): Future[Seq[ApiAttribute]] =
    for {
      tenantUUID <- tenantId.toFutureUUID
      tenant     <- tenantManagementService.getTenant(tenantUUID)
      tenantAttributes = tenant.attributes.mapFilter(attributeFromTenantAttribute)
      attributeIds     = tenantAttributes.map(_.id)
      registryAttributes <- attributeRegistryService.getBulkAttributes(attributeIds)
    } yield Utils.tenantAttributesToApi(tenantAttributes, registryAttributes.attributes)

  override def getTenant(tenantId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerTenant: ToEntityMarshaller[Tenant]
  ): Route = {
    val result: Future[Tenant] = for {
      tenant       <- tenantId.toFutureUUID >>= tenantManagementService.getTenant
      selfcareUUID <- tenant.selfcareId.traverse(_.toFutureUUID)
      attributes   <- attributeRegistryService.getBulkAttributes(Utils.tenantAttributesIds(tenant)).map(_.attributes)
    } yield tenant.toApi(selfcareUUID, attributes)

    onComplete(result) {
      handleError(s"Error retrieving tenant with tenantId $tenantId)") orElse { case Success(t) =>
        getTenant200(t)
      }
    }
  }
}
