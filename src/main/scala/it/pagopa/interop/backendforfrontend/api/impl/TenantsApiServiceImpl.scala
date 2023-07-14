package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.TenantsApiService
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service.types.TenantProcessServiceTypes.AdaptableTenantAttribute.AdaptableTenantAttributeOps
import it.pagopa.interop.backendforfrontend.service.types.TenantProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.{
  SelfcareClientService,
  AttributeRegistryManagementService,
  TenantProcessService
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.tenantprocess.client.model.{TenantAttribute => DepTenantAttribute}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class TenantsApiServiceImpl(
  attributeRegistryService: AttributeRegistryManagementService,
  tenantProcessService: TenantProcessService,
  selfcareClient: SelfcareClientService
)(implicit ec: ExecutionContext)
    extends TenantsApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getTenants(name: Option[String], limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerTenants: ToEntityMarshaller[Tenants],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    def fillLogo(tenant: TenantEntry): Future[TenantEntry] = for {
      selfcareUuid <- tenant.selfcareId.traverse(_.toFutureUUID)
      institution  <- selfcareUuid.traverse(selfcareClient.getInstitution)
    } yield tenant.copy(logoUrl = institution.fold[Option[String]](none)(_.logo))

    val offset: Int             = 0
    val result: Future[Tenants] =
      for {
        pagedResults <- tenantProcessService.getTenants(name = name, offset = offset, limit = limit)
        tenants = pagedResults.results.map(t => TenantEntry(id = t.id, name = t.name, selfcareId = t.selfcareId))
        results <- Future.traverse(tenants)(fillLogo)
      } yield Tenants(
        results = results,
        pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
      )

    onComplete(result) {
      handleError(s"Error retrieving tenants for name $name, offset $offset, limit $limit") orElse { case Success(r) =>
        getTenants200(r)
      }
    }
  }

  override def getProducers(q: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCompactOrganization: ToEntityMarshaller[CompactOrganizations]
  ): Route = {
    val result: Future[CompactOrganizations] =
      for {
        pagedResults <- tenantProcessService.getProducers(name = q, offset = offset, limit = limit)
      } yield CompactOrganizations(
        results = pagedResults.results.map(t => CompactOrganization(id = t.id, name = t.name)),
        pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
      )

    onComplete(result) {
      handleError(s"Error retrieving producers for name $q, offset $offset, limit $limit") orElse { case Success(r) =>
        getProducers200(r)
      }
    }
  }

  override def getConsumers(q: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCompactOrganization: ToEntityMarshaller[CompactOrganizations]
  ): Route = {
    val result: Future[CompactOrganizations] =
      for {
        pagedResults <- tenantProcessService.getConsumers(name = q, offset = offset, limit = limit)
      } yield CompactOrganizations(
        results = pagedResults.results.map(t => CompactOrganization(id = t.id, name = t.name)),
        pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
      )

    onComplete(result) {
      handleError(s"Error retrieving consumers for name $q, offset $offset, limit $limit") orElse { case Success(r) =>
        getConsumers200(r)
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
      ()         <- tenantProcessService.updateTenant(tenantUUID, tenantDelta.toExternalModel)
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
      tenant     <- tenantProcessService.getTenant(tenantUUID)
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
      tenant       <- tenantId.toFutureUUID >>= tenantProcessService.getTenant
      selfcareUUID <- tenant.selfcareId.traverse(_.toFutureUUID)
      attributes   <- attributeRegistryService.getBulkAttributes(Utils.tenantAttributesIds(tenant)).map(_.attributes)
    } yield tenant.toApi(selfcareUUID, attributes)

    onComplete(result) {
      handleError(s"Error retrieving tenant with tenantId $tenantId)") orElse { case Success(t) =>
        getTenant200(t)
      }
    }
  }

  override def updateVerifiedAttribute(
    tenantId: String,
    attributeId: String,
    seed: UpdateVerifiedTenantAttributeSeed
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    val result: Future[Unit] = for {
      tenantUuid    <- tenantId.toFutureUUID
      attributeUuid <- attributeId.toFutureUUID
      _             <- tenantProcessService.updateVerifiedAttribute(tenantUuid, attributeUuid, seed.toSeed).void
    } yield ()

    onComplete(result) {
      handleError(s"Error updating expirationDate for verified attribute ${attributeId} to tenant $tenantId") orElse {
        case Success(_) =>
          updateVerifiedAttribute204
      }
    }
  }
}
