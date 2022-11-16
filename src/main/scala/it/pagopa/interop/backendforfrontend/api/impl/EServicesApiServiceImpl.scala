package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.client.{model => AgreementProcess}
import it.pagopa.interop.agreementprocess.lifecycle.AttributesRules.certifiedAttributesSatisfied
import it.pagopa.interop.backendforfrontend.api.EservicesApiService
import it.pagopa.interop.backendforfrontend.error.BFFErrors._
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service._
import it.pagopa.interop.backendforfrontend.service.types.AgreementProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.CatalogProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.TenantManagementServiceTypes._
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils._
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class EServicesApiServiceImpl(
  agreementProcessService: AgreementProcessService,
  attributeRegistryManagementService: AttributeRegistryManagementService,
  catalogProcessService: CatalogProcessService,
  tenantManagementService: TenantManagementService,
  partyProcessService: PartyProcessService
)(implicit ec: ExecutionContext)
    extends EservicesApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private val ACTIVE_DESCRIPTOR_STATES_FILTER: List[CatalogProcess.EServiceDescriptorState] = List(
    CatalogProcess.EServiceDescriptorState.PUBLISHED,
    CatalogProcess.EServiceDescriptorState.SUSPENDED,
    CatalogProcess.EServiceDescriptorState.DEPRECATED
  )

  private val SUBSCRIBED_AGREEMENT_STATES: Set[AgreementProcess.AgreementState] = Set(
    AgreementProcess.AgreementState.PENDING,
    AgreementProcess.AgreementState.ACTIVE,
    AgreementProcess.AgreementState.SUSPENDED
  )

  override def getCatalogEServiceDescriptor(eserviceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCatalogEServiceDescriptor: ToEntityMarshaller[CatalogEServiceDescriptor]
  ): Route = {
    val result: Future[CatalogEServiceDescriptor] = for {
      requesterId                    <- getOrganizationIdFuture(contexts)
      (eserviceUUID, descriptorUUID) <- eserviceId.toFutureUUID.zip(descriptorId.toFutureUUID)
      eService                       <- catalogProcessService.getEServiceById(eserviceUUID)
      descriptor                     <- eService.descriptors
        .find(_.id === descriptorUUID)
        .toFuture(EServiceDescriptorNotFound(eService.id.toString, descriptorId))
      attributes                     <- attributeRegistryManagementService
        .getBulkAttributes(extractIdsFromAttributes(eService.attributes))(contexts)
        .map(_.attributes)
      requesterTenant                <- tenantManagementService.getTenant(eService.producerId)
      agreement                      <- agreementProcessService
        .getAgreements(
          consumerId = requesterId.some,
          eServiceId = eserviceId.some,
          descriptorId = descriptorId.some,
          states = Seq.empty,
          latest = None
        )
        .map(_.headOption)
    } yield CatalogEServiceDescriptor(
      id = descriptor.id,
      version = descriptor.version,
      description = descriptor.description,
      interface = descriptor.interface.map(_.toApi),
      docs = descriptor.docs.map(_.toApi),
      state = descriptor.state.toApi,
      audience = descriptor.audience,
      voucherLifespan = descriptor.voucherLifespan,
      dailyCallsPerConsumer = descriptor.dailyCallsPerConsumer,
      dailyCallsTotal = descriptor.dailyCallsTotal,
      agreementApprovalPolicy = descriptor.agreementApprovalPolicy.toApi,
      eservice = CatalogEService(
        id = eService.id,
        name = eService.name,
        description = eService.description,
        technology = eService.technology.toApi,
        attributes = eService.attributes.toApi(attributes),
        descriptors = eService.descriptors
          .filter(_.state != CatalogProcess.EServiceDescriptorState.DRAFT)
          .map(_.toCompactDescriptor),
        agreement = agreement.map(a => CompactAgreement(id = a.id, state = a.state.toApi)),
        isMine = eService.producerId.toString == requesterId,
        canSubscribe = certifiedAttributesSatisfied(
          eService.attributes.toManagement,
          requesterTenant.attributes.mapFilter(_.certified)
        ),
        isSubscribed = agreement.exists(a => SUBSCRIBED_AGREEMENT_STATES.contains(a.state)),
        activeDescriptor =
          getActiveDescriptor(eService).map(ad => CompactDescriptor(ad.id, ad.state.toApi, ad.version)),
        mail = requesterTenant.mails.headOption.map(_.toApi)
      )
    )

    onComplete(result) {
      handleError(s"Error retrieving descriptor $descriptorId of eservice $eserviceId") orElse {
        case Failure(x: EServiceDescriptorNotFound) =>
          logger.warn(x.getMessage)
          getCatalogEServiceDescriptor404(problemOf(StatusCodes.NotFound, x))
        case Success(descriptor)                    => getCatalogEServiceDescriptor200(descriptor)
      }
    }
  }

  private def extractIdsFromAttributes(attributes: CatalogProcess.Attributes): Seq[UUID] =
    attributes.certified.flatMap(extractIdsFromAttribute) ++
      attributes.declared.flatMap(extractIdsFromAttribute) ++
      attributes.verified.flatMap(extractIdsFromAttribute)

  private def extractIdsFromAttribute(attribute: CatalogProcess.Attribute): Seq[UUID] = {
    val fromSingle: Seq[UUID] = attribute.single.toSeq.map(_.id)
    val fromGroup: Seq[UUID]  = attribute.group.toSeq.flatMap(_.map(_.id))

    fromSingle ++ fromGroup
  }

  override def getEServicesCatalog(q: Option[String], producersIds: String, states: String, offset: Int, limit: Int)(
    implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCatalogEServices: ToEntityMarshaller[CatalogEServices]
  ): Route = {
    val result = for {
      requesterId       <- getOrganizationIdFutureUUID(contexts)
      apiStates         <- parseArrayParameters(states).traverse(EServiceDescriptorState.fromValue).toFuture
      producersUuids    <- parseArrayParameters(producersIds).traverse(_.toFutureUUID)
      pagedResults      <- catalogProcessService.getEServices(
        name = q,
        eServicesIds = Nil,
        producersIds = producersUuids,
        states = apiStates.map(CatalogProcess.EServiceDescriptorState.fromApi),
        offset = offset,
        limit = limit
      )
      enhancedEServices <- Future.traverse(pagedResults.eservices)(enhanceCatalogEServiceItem(requesterId))
    } yield CatalogEServices(
      eservices = enhancedEServices,
      pagination = Pagination(offset = offset, limit = limit, totalResults = pagedResults.totalCount)
    )

    onComplete(result) {
      handleError(s"Error retrieving Catalog EServices") orElse { case Success(eServices) =>
        getEServicesCatalog200(eServices)
      }
    }
  }

  override def getProducerEServices(q: Option[String], consumersIds: String, offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProducerEServices: ToEntityMarshaller[ProducerEServices],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result = for {
      producerId   <- getOrganizationIdFutureUUID(contexts)
      eServicesIds <- getProducerEServicesIds(producerId, parseArrayParameters(consumersIds))
      pagedResults <- catalogProcessService.getEServices(
        name = q,
        eServicesIds = eServicesIds,
        producersIds = Nil,
        states = Nil,
        offset = offset,
        limit = limit
      )
    } yield ProducerEServices(
      eservices = pagedResults.eservices.map(enhanceProducerEService),
      pagination = Pagination(offset = offset, limit = limit, totalResults = pagedResults.totalCount)
    )

    onComplete(result) {
      handleError(s"Error retrieving Producer EServices") orElse { case Success(eServices) =>
        getProducerEServices200(eServices)
      }
    }
  }

  private def getProducerEServicesIds(producerId: UUID, consumersIds: List[String])(implicit
    contexts: Seq[(String, String)]
  ): Future[List[UUID]] =
    Future
      .traverse(consumersIds)(consumerId =>
        // TODO This call will be paginated
        agreementProcessService.getAgreements(
          producerId = producerId.toString.some,
          consumerId.some,
          states = List(AgreementProcess.AgreementState.ACTIVE, AgreementProcess.AgreementState.SUSPENDED)
        )
      )
      .map(_.flatten.map(_.eserviceId).distinct)

  private def enhanceCatalogEServiceItem(
    requesterId: UUID
  )(eService: CatalogProcess.EService)(implicit contexts: Seq[(String, String)]): Future[CatalogEServiceItem] = for {
    // TODO Use directly the tenant once the name will be added to its model
    producerTenant      <- tenantManagementService.getTenant(eService.producerId)
    selfcareId          <- producerTenant.selfcareId.toFuture(MissingSelfcareId(producerTenant.id))
    producerInstitution <- partyProcessService.getInstitution(selfcareId)
    // End TODO

    requesterTenant <-
      if (requesterId != eService.producerId) tenantManagementService.getTenant(requesterId)
      else Future.successful(producerTenant)

    activeDescriptor = getActiveDescriptor(eService)

    agreement <- activeDescriptor.flatTraverse(d =>
      agreementProcessService
        .getAgreements(
          consumerId = requesterId.toString.some,
          eServiceId = eService.id.toString.some,
          descriptorId = d.id.toString.some,
          states = Nil
        )
        .map(_.headOption)
    )
  } yield CatalogEServiceItem(
    id = eService.id,
    name = eService.name,
    description = eService.description,
    producer = CompactOrganization(id = eService.producerId, name = producerInstitution.description),
    agreement = agreement.map(a => CompactAgreement(id = a.id, state = a.state.toApi)),
    isMine = eService.producerId == requesterId,
    canSubscribe =
      certifiedAttributesSatisfied(eService.attributes.toManagement, requesterTenant.attributes.mapFilter(_.certified)),
    activeDescriptor = activeDescriptor.map(_.toCompactDescriptor)
  )

  private def enhanceProducerEService(eService: CatalogProcess.EService) = ProducerEService(
    id = eService.id,
    name = eService.name,
    activeDescriptor = getActiveDescriptor(eService).map(_.toCompactDescriptor),
    draftDescriptor = getDraftDescriptor(eService).map(_.toCompactDescriptor)
  )

  private def getActiveDescriptor(eService: CatalogProcess.EService): Option[CatalogProcess.EServiceDescriptor] =
    eService.descriptors
      .filter(d => ACTIVE_DESCRIPTOR_STATES_FILTER.contains(d.state))
      .sortBy(_.version.toInt)
      .lastOption

  private def getDraftDescriptor(eService: CatalogProcess.EService): Option[CatalogProcess.EServiceDescriptor] =
    eService.descriptors.find(_.state == CatalogProcess.EServiceDescriptorState.DRAFT)

}
