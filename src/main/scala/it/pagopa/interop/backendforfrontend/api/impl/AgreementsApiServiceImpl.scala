package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.{Route, StandardRoute}
import cats.implicits._
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.agreementprocess.client.{model => AgreementProcess}
import it.pagopa.interop.attributeregistrymanagement.client.{model => AttributeRegistry}
import it.pagopa.interop.backendforfrontend.api.AgreementsApiService
import it.pagopa.interop.backendforfrontend.error.BFFErrors.AgreementDescriptorNotFound
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service.types.AgreementProcessServiceTypes.{AgreementPayloadConverter, _}
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.{MgmtAttributesResponse, _}
import it.pagopa.interop.backendforfrontend.service.types.CatalogManagementServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.TenantManagementServiceTypes._
import it.pagopa.interop.backendforfrontend.service._
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagement}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.{GenericError, ResourceNotFoundError}
import it.pagopa.interop.tenantmanagement.client.{model => TenantManagement}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class AgreementsApiServiceImpl(
  agreementProcessService: AgreementProcessService,
  attributeRegistryService: AttributeRegistryManagementService,
  catalogManagementService: CatalogManagementService,
  partyProcessService: PartyProcessService,
  tenantManagementService: TenantManagementService
)(implicit ec: ExecutionContext)
    extends AgreementsApiService {

  private val logger = Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def createAgreement(payload: AgreementPayload)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {
    val result: Future[CreatedResource] = for {
      result <- agreementProcessService.createAgreement(payload.toSeed)
    } yield CreatedResource(result.id)

    onComplete(result) {
      case Success(resource) =>
        createAgreement200(resource)
      case Failure(e)        =>
        val message =
          s"Error while creating agreement for EService ${payload.eserviceId} and Descriptor ${payload.descriptorId}"
        logger.error(message, e)
        internalServerError(message)
    }
  }

  override def getAgreementById(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = {
    val agreement: Future[Agreement] = for {
      agreementUuid <- agreementId.toFutureUUID
      agreement     <- agreementProcessService.getAgreementById(agreementUuid)
      apiAgreement  <- enhanceAgreement(agreement)
    } yield apiAgreement

    onComplete(agreement) {
      case Success(agreement)                 => getAgreementById200(agreement)
      case Failure(ex: ResourceNotFoundError) =>
        logger.error(s"Error while retrieving agreement  $agreementId", ex)
        getAgreementById404(problemOf(StatusCodes.NotFound, ex))
      case Failure(e)                         =>
        val message = s"Error while retrieving agreement $agreementId"
        logger.error(message, e)
        internalServerError(message)
    }
  }

  override def activateAgreement(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = {
    val agreement: Future[Agreement] = for {
      agreementUuid <- agreementId.toFutureUUID
      agreement     <- agreementProcessService.activateAgreement(agreementUuid)
      apiAgreement  <- enhanceAgreement(agreement)
    } yield apiAgreement

    onComplete(agreement) {
      case Success(agreement)                 => activateAgreement200(agreement)
      case Failure(ex: ResourceNotFoundError) =>
        logger.error(s"Error while activating agreement  $agreementId", ex)
        activateAgreement404(problemOf(StatusCodes.NotFound, ex))
      case Failure(e)                         =>
        val message = s"Error while activating agreement $agreementId"
        logger.error(message, e)
        internalServerError(message)
    }
  }

  override def submitAgreement(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = {
    val agreement: Future[Agreement] = for {
      agreementUuid <- agreementId.toFutureUUID
      agreement     <- agreementProcessService.submitAgreement(agreementUuid)
      apiAgreement  <- enhanceAgreement(agreement)
    } yield apiAgreement

    onComplete(agreement) {
      case Success(agreement)                 => submitAgreement200(agreement)
      case Failure(ex: ResourceNotFoundError) =>
        logger.error(s"Error while submitting agreement $agreementId", ex)
        submitAgreement404(problemOf(StatusCodes.NotFound, ex))
      case Failure(e)                         =>
        val message = s"Error while submitting agreement $agreementId"
        logger.error(message, e)
        internalServerError(message)
    }
  }

  override def suspendAgreement(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = {
    val agreement: Future[Agreement] = for {
      agreementUuid <- agreementId.toFutureUUID
      agreement     <- agreementProcessService.suspendAgreement(agreementUuid)
      apiAgreement  <- enhanceAgreement(agreement)
    } yield apiAgreement

    onComplete(agreement) {
      case Success(agreement)                 => suspendAgreement200(agreement)
      case Failure(ex: ResourceNotFoundError) =>
        logger.error(s"Error while suspending agreement $agreementId", ex)
        suspendAgreement404(problemOf(StatusCodes.NotFound, ex))
      case Failure(e)                         =>
        val message = s"Error while suspending agreement $agreementId"
        logger.error(message, e)
        internalServerError(message)
    }
  }

  override def upgradeAgreement(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = {
    val agreement: Future[Agreement] = for {
      agreementUuid <- agreementId.toFutureUUID
      agreement     <- agreementProcessService.upgradeAgreement(agreementUuid)
      apiAgreement  <- enhanceAgreement(agreement)
    } yield apiAgreement

    onComplete(agreement) {
      case Success(agreement)                 => upgradeAgreement200(agreement)
      case Failure(ex: ResourceNotFoundError) =>
        logger.error(s"Error while upgrading agreement  $agreementId", ex)
        upgradeAgreement404(problemOf(StatusCodes.NotFound, ex))
      case Failure(e)                         =>
        val message = s"Error while upgrading agreement $agreementId"
        logger.error(message, e)
        internalServerError(message)
    }
  }

  def enhanceAgreement(
    agreement: AgreementProcess.Agreement
  )(implicit contexts: Seq[(String, String)]): Future[Agreement] = for {
    producer          <- partyProcessService.getInstitution(agreement.producerId)
    consumer          <- partyProcessService.getInstitution(agreement.consumerId)
    consumerTenant    <- tenantManagementService.getTenant(agreement.consumerId)
    eService          <- catalogManagementService.getEService(agreement.eserviceId)
    currentDescriptor <- eService.descriptors
      .find(_.id == agreement.descriptorId)
      .toFuture(AgreementDescriptorNotFound(agreement.id))
    activeDescriptor = eService.descriptors.sortBy(_.version.toInt).lastOption

    allAttributesIds = (eServiceAttributesIds(eService) ++ tenantAttributesIds(consumerTenant)).distinct
    attributes <- attributeRegistryService.getBulkAttributes(allAttributesIds)

    agreementVerifiedAttrs  = filterAttributes(attributes, agreement.verifiedAttributes.map(_.id))
      .map(_.toVerifiedAttribute)
    agreementCertifiedAttrs = filterAttributes(attributes, agreement.certifiedAttributes.map(_.id))
      .map(_.toCertifiedAttribute)
    agreementDeclaredAttrs  = filterAttributes(attributes, agreement.declaredAttributes.map(_.id))
      .map(_.toDeclaredAttribute)

    tenantAttributes = consumerTenant.attributes.map(enhanceTenantAttribute(attributes, _))
  } yield Agreement(
    id = agreement.id,
    descriptorId = agreement.descriptorId,
    producer = Tenant(id = agreement.producerId, name = producer.description),
    consumer =
      TenantWithAttributes(id = agreement.consumerId, name = consumer.description, attributes = tenantAttributes),
    eservice = EService(
      id = agreement.eserviceId,
      name = eService.name,
      version = currentDescriptor.version,
      activeDescriptor = activeDescriptor.map(_.toActiveDescriptor)
    ),
    state = agreement.state.toApi,
    verifiedAttributes = agreementVerifiedAttrs,
    certifiedAttributes = agreementCertifiedAttrs,
    declaredAttributes = agreementDeclaredAttrs,
    suspendedByConsumer = agreement.suspendedByConsumer,
    suspendedByProducer = agreement.suspendedByProducer,
    suspendedByPlatform = agreement.suspendedByPlatform,
    consumerNotes = agreement.consumerNotes,
    consumerDocuments = agreement.consumerDocuments.map(_.toApi),
    createdAt = agreement.createdAt,
    updatedAt = agreement.updatedAt
  )

  def eServiceAttributesIds(eService: CatalogManagement.EService): Seq[UUID] = {
    val attrs: Seq[CatalogManagement.Attribute] =
      eService.attributes.verified ++ eService.attributes.declared ++ eService.attributes.certified
    attrs
      .mapFilter(a =>
        (a.single, a.group) match {
          case (Some(s), Some(g)) => Some(s :: g.toList)
          case (Some(s), None)    => Some(s :: Nil)
          case (None, g)          => g
        }
      )
      .flatten
      .map(_.id)
  }

  def tenantAttributesIds(tenant: TenantManagement.Tenant): Seq[UUID] =
    tenant.attributes.mapFilter(_.verified.map(_.id)) ++
      tenant.attributes.mapFilter(_.certified.map(_.id)) ++
      tenant.attributes.mapFilter(_.declared.map(_.id))

  def filterAttributes(
    registryAttributes: MgmtAttributesResponse,
    filterIds: Seq[UUID]
  ): Seq[AttributeRegistry.Attribute] =
    filterIds.flatMap(id => registryAttributes.attributes.find(_.id == id))

  def enhanceTenantAttribute(
    registryAttributes: MgmtAttributesResponse,
    tenantAttribute: TenantManagement.TenantAttribute
  ): TenantAttribute = {
    val certified = tenantAttribute.certified.map(a => (a, registryAttributes.attributes.find(_.id == a.id))).collect {
      case (a1, Some(a2)) => a1.toApi(a2.name)
    }
    val declared  = tenantAttribute.declared.map(a => (a, registryAttributes.attributes.find(_.id == a.id))).collect {
      case (a1, Some(a2)) => a1.toApi(a2.name)
    }
    val verified  = tenantAttribute.verified.map(a => (a, registryAttributes.attributes.find(_.id == a.id))).collect {
      case (a1, Some(a2)) => a1.toApi(a2.name)
    }

    TenantAttribute(declared = declared, certified = certified, verified = verified)
  }

  private def internalServerError(
    errorMessage: String
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): StandardRoute = {
    val statusCode = StatusCodes.InternalServerError
    complete(statusCode.intValue, problemOf(statusCode, GenericError(errorMessage)))
  }

}
