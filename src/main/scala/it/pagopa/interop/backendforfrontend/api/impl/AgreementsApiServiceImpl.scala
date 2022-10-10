package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.client.{model => AgreementProcess}
import it.pagopa.interop.attributeregistrymanagement.client.{model => AttributeRegistry}
import it.pagopa.interop.backendforfrontend.api.AgreementsApiService
import it.pagopa.interop.backendforfrontend.error.BFFErrors.AgreementDescriptorNotFound
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service._
import it.pagopa.interop.backendforfrontend.service.types.AgreementProcessServiceTypes.{AgreementPayloadConverter, _}
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.{MgmtAttributesResponse, _}
import it.pagopa.interop.backendforfrontend.service.types.CatalogManagementServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.TenantManagementServiceTypes.AdaptableTenantAttribute._
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagement}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.tenantmanagement.client.{model => TenantManagement}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class AgreementsApiServiceImpl(
  agreementProcessService: AgreementProcessService,
  attributeRegistryService: AttributeRegistryManagementService,
  catalogManagementService: CatalogManagementService,
  partyProcessService: PartyProcessService,
  tenantManagementService: TenantManagementService
)(implicit ec: ExecutionContext)
    extends AgreementsApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def createAgreement(payload: AgreementPayload)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {
    val result: Future[CreatedResource] = for {
      result <- agreementProcessService.createAgreement(payload.toSeed)
    } yield CreatedResource(result.id)

    onComplete(result) {
      handleError(
        s"Error creating agreement for EService ${payload.eserviceId} and Descriptor ${payload.descriptorId}"
      ) orElse { case Success(resource) =>
        createAgreement200(resource)

      }
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
      handleError(s"Error retrieving agreement $agreementId") orElse { case Success(agreement) =>
        getAgreementById200(agreement)
      }
    }
  }

  override def getAgreements(
    producerId: Option[String],
    consumerId: Option[String],
    eServiceId: Option[String],
    descriptorId: Option[String],
    states: String,
    latest: Option[Boolean]
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val agreements: Future[Seq[Agreement]] = for {
      statesEnums   <- parseArrayParameters(states).traverse(AgreementState.fromValue).toFuture
      agreements    <- agreementProcessService.getAgreements(
        producerId = producerId,
        consumerId = consumerId,
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        states = statesEnums.map(AgreementProcess.AgreementState.fromApi),
        latest = latest
      )
      apiAgreements <- Future.traverse(agreements)(enhanceAgreement)
    } yield apiAgreements

    onComplete(agreements) {
      handleError(s"Error retrieving agreements") orElse { case Success(agreements) => getAgreements200(agreements) }
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
      handleError(s"Error activating agreement $agreementId") orElse { case Success(agreement) =>
        activateAgreement200(agreement)
      }
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
      handleError(s"Error submitting agreement $agreementId") orElse { case Success(agreement) =>
        submitAgreement200(agreement)
      }
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
      handleError(s"Error suspending agreement $agreementId") orElse { case Success(agreement) =>
        suspendAgreement200(agreement)
      }
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
      handleError(s"Error upgrading agreement $agreementId") orElse { case Success(agreement) =>
        upgradeAgreement200(agreement)
      }
    }
  }

  def getDescription(selfcareId: String)(implicit contexts: Seq[(String, String)]): Future[String] =
    partyProcessService.getInstitution(selfcareId).map(_.description)

  def parallelGet(agreement: AgreementProcess.Agreement)(implicit
    contexts: Seq[(String, String)]
  ): Future[(String, String, TenantManagement.Tenant, CatalogManagement.EService)] =
    for {
      (consumerTenant, producerTenant) <- tenantManagementService
        .getTenant(agreement.consumerId)
        .zip(tenantManagementService.getTenant(agreement.producerId))

      (consumerDescription, producerDescription) <- consumerTenant.selfcareId
        .fold(Future.successful(consumerTenant.id.toString()))(getDescription)
        .zip(producerTenant.selfcareId.fold(Future.successful(producerTenant.id.toString()))(getDescription))

      eService <- catalogManagementService.getEService(agreement.eserviceId)
    } yield (producerDescription, consumerDescription, consumerTenant, eService)

  def enhanceAgreement(
    agreement: AgreementProcess.Agreement
  )(implicit contexts: Seq[(String, String)]): Future[Agreement] = for {
    (producerDescription, consumerDescription, consumerTenant, eService) <- parallelGet(agreement)
    currentDescriptor                                                    <- eService.descriptors
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

    tenantAttributes = enhanceTenantAttributes(consumerTenant.attributes, attributes)
  } yield Agreement(
    id = agreement.id,
    descriptorId = agreement.descriptorId,
    producer = Tenant(id = agreement.producerId, name = producerDescription),
    consumer =
      TenantWithAttributes(id = agreement.consumerId, name = consumerDescription, attributes = tenantAttributes),
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

  def enhanceTenantAttributes(
    tenantAttributes: Seq[TenantManagement.TenantAttribute],
    registryAttributes: MgmtAttributesResponse
  ): Seq[TenantAttribute] = {

    val registryAttributesMap = registryAttributes.attributes.fproductLeft(_.id).toMap

    tenantAttributes.collect {
      case TenantManagement.TenantAttribute(Some(declared), None, None)  =>
        TenantAttribute(declared = Utils.tenantAttributeToApi(declared, registryAttributesMap))
      case TenantManagement.TenantAttribute(None, Some(certified), None) =>
        TenantAttribute(certified = Utils.tenantAttributeToApi(certified, registryAttributesMap))
      case TenantManagement.TenantAttribute(None, None, Some(verified))  =>
        TenantAttribute(verified = Utils.tenantAttributeToApi(verified, registryAttributesMap))
    }
  }
}
