package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.attributeregistrymanagement.client.model.Attribute
import it.pagopa.interop.backendforfrontend.api.TenantsApiService
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.AttributeConverter
import it.pagopa.interop.backendforfrontend.service.types.TenantManagementServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.TenantProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.{
  AttributeRegistryManagementService,
  TenantManagementService,
  TenantProcessService
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.tenantmanagement.client.model.{
  CertifiedTenantAttribute => DepCertifiedTenantAttribute,
  DeclaredTenantAttribute => DepDeclaredTenantAttribute,
  VerifiedTenantAttribute => DepVerifiedTenantAttribute
}

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

    def getAttributeSafe(attribute: DepCertifiedTenantAttribute): Future[Option[Attribute]] =
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

  override def getVerifiedAttributes(tenantId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerVerifiedAttributesResponse: ToEntityMarshaller[VerifiedAttributesResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    def getAttributeSafe(attribute: DepVerifiedTenantAttribute): Future[Option[Attribute]] =
      attributeRegistryService
        .getAttributeById(attribute.id)
        .redeem(
          e => {
            logger.error(s"Unable to find attribute ${attribute.id}", e)
            Option.empty[Attribute]
          },
          Option(_)
        )

    def toVerifiedAttributes(
      tenantAttributes: Seq[DepVerifiedTenantAttribute],
      registryAttributes: Seq[Attribute]
    ): Seq[VerifiedTenantAttribute] = {
      val registryMap = registryAttributes.map(a => (a.id, a)).toMap
      tenantAttributes
        .map(a => (a, registryMap.get(a.id)))
        .collect { case (ta, Some(ra)) => ta.toApi(ra.name, ra.description) }
    }

    val result: Future[VerifiedAttributesResponse] = for {
      tenantUUID <- tenantId.toFutureUUID
      tenant     <- tenantManagementService.getTenant(tenantUUID)
      tenantVerifiedAttributes = tenant.attributes.mapFilter(_.verified)
      registryAttributes <- Future.traverse(tenantVerifiedAttributes)(getAttributeSafe).map(_.flatten)
    } yield VerifiedAttributesResponse(toVerifiedAttributes(tenantVerifiedAttributes, registryAttributes))

    onComplete(result) {
      handleError(s"Error retrieving verified attributes for tenant $tenantId") orElse { case Success(attributes) =>
        getVerifiedAttributes200(attributes)
      }
    }
  }

  override def getDeclaredAttributes(tenantId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerDeclaredAttributesResponse: ToEntityMarshaller[DeclaredAttributesResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    def getAttributeSafe(attribute: DepDeclaredTenantAttribute): Future[Option[Attribute]] =
      attributeRegistryService
        .getAttributeById(attribute.id)
        .redeem(
          e => {
            logger.error(s"Unable to find attribute ${attribute.id}", e)
            Option.empty[Attribute]
          },
          Option(_)
        )

    def toDeclaredAttributes(
      tenantAttributes: Seq[DepDeclaredTenantAttribute],
      registryAttributes: Seq[Attribute]
    ): Seq[DeclaredTenantAttribute] = {
      val registryMap = registryAttributes.map(a => (a.id, a)).toMap
      tenantAttributes
        .map(a => (a, registryMap.get(a.id)))
        .collect { case (ta, Some(ra)) => ta.toApi(ra.name, ra.description) }
    }

    val result: Future[DeclaredAttributesResponse] = for {
      tenantUUID <- tenantId.toFutureUUID
      tenant     <- tenantManagementService.getTenant(tenantUUID)
      tenantDeclaredAttributes = tenant.attributes.mapFilter(_.declared)
      registryAttributes <- Future.traverse(tenantDeclaredAttributes)(getAttributeSafe).map(_.flatten)
    } yield DeclaredAttributesResponse(toDeclaredAttributes(tenantDeclaredAttributes, registryAttributes))

    onComplete(result) {
      handleError(s"Error retrieving declared attributes for tenant $tenantId") orElse { case Success(attributes) =>
        getDeclaredAttributes200(attributes)
      }
    }
  }
}
