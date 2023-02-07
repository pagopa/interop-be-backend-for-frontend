package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{ContentType, HttpEntity, MessageEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.client.{model => AgreementProcess}
import it.pagopa.interop.agreementprocess.lifecycle.AttributesRules.certifiedAttributesSatisfied
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagementDependency}
import it.pagopa.interop.backendforfrontend.api.EservicesApiService
import it.pagopa.interop.backendforfrontend.api.impl.ResponseHandlers.getEServiceDocumentByIdResponse
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.BFFErrors._
import it.pagopa.interop.backendforfrontend.error.CatalogProcessErrors.ContentTypeParsingError
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service._
import it.pagopa.interop.backendforfrontend.service.types.AgreementProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.CatalogProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.TenantManagementServiceTypes._
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt.{ADMIN_ROLE, API_ROLE, M2M_ROLE, SECURITY_ROLE, authorize}
import it.pagopa.interop.tenantmanagement.client.{model => TenantManagement}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils._
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions._

import java.io.{File, FileOutputStream}
import java.nio.file.{Files, Path}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class EServicesApiServiceImpl(
  agreementProcessService: AgreementProcessService,
  attributeRegistryManagementService: AttributeRegistryManagementService,
  catalogProcessService: CatalogProcessService,
  tenantManagementService: TenantManagementService,
  partyProcessService: PartyProcessService,
  catalogManagementService: CatalogManagementService,
  fileManager: FileManager
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

  override def createEService(eServiceSeed: EServiceSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {
    val result: Future[CreatedResource] =
      catalogProcessService.createEService(eServiceSeed.toProcess)(contexts).map(_.toApi)

    onComplete(result) {
      handleError(s"Error creating eservice with seed: $eServiceSeed") orElse { case Success(eservice) =>
        createEService200(eservice)
      }
    }
  }
  override def activateDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Unit] =
      catalogProcessService.activateDescriptor(eServiceId, descriptorId)(contexts)

    onComplete(result) {
      handleError(s"Error activating descriptor $descriptorId on eservice $eServiceId") orElse { case Success(_) =>
        activateDescriptor204
      }
    }
  }

  override def publishDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Unit] =
      catalogProcessService.publishDescriptor(eServiceId, descriptorId)(contexts)

    onComplete(result) {
      handleError(s"Error publishing descriptor $descriptorId for eservice $eServiceId") orElse { case Success(_) =>
        publishDescriptor204
      }
    }
  }

  override def createDescriptor(eServiceId: String, eServiceDescriptorSeed: EServiceDescriptorSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {
    val result: Future[CreatedResource] = for {
      eServiceIdUUID <- eServiceId.toFutureUUID
      descriptor     <- catalogProcessService
        .createDescriptor(eServiceIdUUID, eServiceDescriptorSeed.toProcess)(contexts)
    } yield descriptor.toApi

    onComplete(result) {
      handleError(s"Error creating descriptor with seed: $eServiceDescriptorSeed") orElse { case Success(descriptor) =>
        createDescriptor200(descriptor)
      }
    }
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
      enhancedEServices <- Future.traverse(pagedResults.results)(enhanceCatalogEService(requesterId))
    } yield CatalogEServices(
      results = enhancedEServices,
      pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
    )

    onComplete(result) {
      handleError(s"Error retrieving Catalog EServices") orElse { case Success(eServices) =>
        getEServicesCatalog200(eServices)
      }
    }
  }

  override def getCatalogEServiceDescriptor(eserviceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCatalogEServiceDescriptor: ToEntityMarshaller[CatalogEServiceDescriptor]
  ): Route = {
    val result: Future[CatalogEServiceDescriptor] = for {
      requesterId                    <- getOrganizationIdFutureUUID(contexts)
      (eserviceUUID, descriptorUUID) <- eserviceId.toFutureUUID.zip(descriptorId.toFutureUUID)
      eService                       <- catalogProcessService.getEServiceById(eserviceUUID)
      descriptor                     <- eService.descriptors
        .find(_.id === descriptorUUID)
        .toFuture(EServiceDescriptorNotFound(eService.id.toString, descriptorId))
      attributes                     <- attributeRegistryManagementService
        .getBulkAttributes(extractIdsFromAttributes(eService.attributes))(contexts)
        .map(_.attributes)
      eServiceAttributes             <- eService.attributes.toApi(attributes)
      requesterTenant                <- tenantManagementService.getTenant(requesterId)
      producerTenant                 <- tenantManagementService.getTenant(eService.producerId)
      agreement                      <- agreementProcessService
        .getAgreements(
          consumerId = requesterId.toString.some,
          eServiceId = eserviceId.some,
          descriptorId = descriptorId.some,
          states = Seq.empty
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
      eservice = CatalogDescriptorEService(
        id = eService.id,
        name = eService.name,
        description = eService.description,
        technology = eService.technology.toApi,
        attributes = eServiceAttributes,
        descriptors = getNonDraftDescriptors(eService).map(_.toCompactDescriptor),
        agreement = agreement.map(a => CompactAgreement(id = a.id, state = a.state.toApi)),
        isMine = eService.producerId == requesterId,
        hasCertifiedAttributes = certifiedAttributesSatisfied(
          eService.attributes.toManagement,
          requesterTenant.attributes.mapFilter(_.certified)
        ),
        isSubscribed = agreement.exists(a => SUBSCRIBED_AGREEMENT_STATES.contains(a.state)),
        activeDescriptor =
          getActiveDescriptor(eService).map(ad => CompactDescriptor(ad.id, ad.state.toApi, ad.version)),
        mail = producerTenant.mails.find(_.kind == TenantManagement.MailKind.CONTACT_EMAIL).map(_.toApi)
      )
    )

    onComplete(result) {
      handleError(s"Error retrieving descriptor $descriptorId of eservice $eserviceId from catalog") orElse {
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
        producersIds = List(producerId),
        states = Nil,
        offset = offset,
        limit = limit
      )
    } yield ProducerEServices(
      results = pagedResults.results.map(enhanceProducerEService),
      pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
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

  private def enhanceCatalogEService(
    requesterId: UUID
  )(eService: CatalogProcess.EService)(implicit contexts: Seq[(String, String)]): Future[CatalogEService] = for {
    producerTenant  <- tenantManagementService.getTenant(eService.producerId)
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
  } yield CatalogEService(
    id = eService.id,
    name = eService.name,
    description = eService.description,
    producer = CompactOrganization(id = eService.producerId, name = producerTenant.name),
    agreement = agreement.map(a => CompactAgreement(id = a.id, state = a.state.toApi)),
    isMine = eService.producerId == requesterId,
    hasCertifiedAttributes =
      certifiedAttributesSatisfied(eService.attributes.toManagement, requesterTenant.attributes.mapFilter(_.certified)),
    activeDescriptor = activeDescriptor.map(_.toCompactDescriptor)
  )

  private def enhanceProducerEService(eService: CatalogProcess.EService) = ProducerEService(
    id = eService.id,
    name = eService.name,
    activeDescriptor = getActiveDescriptor(eService).map(_.toCompactDescriptor),
    draftDescriptor = getDraftDescriptor(eService).map(_.toCompactDescriptor)
  )

  override def getProducerEServiceDetails(eserviceId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProducerEServiceDetails: ToEntityMarshaller[ProducerEServiceDetails],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[ProducerEServiceDetails] = for {
      requesterId        <- getOrganizationIdFutureUUID(contexts)
      eserviceUUID       <- eserviceId.toFutureUUID
      eService           <- catalogProcessService.getEServiceById(eserviceUUID)
      // This is an anti-pattern, but it's the cleanest solution
      _                  <- isTheProducer(eService, requesterId)
      attributes         <- attributeRegistryManagementService
        .getBulkAttributes(extractIdsFromAttributes(eService.attributes))(contexts)
        .map(_.attributes)
      eServiceAttributes <- eService.attributes.toApi(attributes)
    } yield ProducerEServiceDetails(
      id = eService.id,
      name = eService.name,
      description = eService.description,
      technology = eService.technology.toApi,
      attributes = eServiceAttributes
    )

    onComplete(result) {
      handleError(s"Error retrieving producer eservice $eserviceId") orElse {
        case Failure(x: EServiceDescriptorNotFound) =>
          logger.warn(x.getMessage)
          getProducerEServiceDetails404(problemOf(StatusCodes.NotFound, x))
        case Failure(x: InvalidEServiceRequester)   =>
          logger.warn(x.getMessage)
          getProducerEServiceDetails403(problemOf(StatusCodes.Forbidden, x))
        case Success(eService)                      => getProducerEServiceDetails200(eService)
      }
    }
  }

  override def getProducerEServiceDescriptor(eserviceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerProducerDescriptor: ToEntityMarshaller[ProducerEServiceDescriptor]
  ): Route = {
    val result: Future[ProducerEServiceDescriptor] = for {
      requesterId                    <- getOrganizationIdFutureUUID(contexts)
      (eserviceUUID, descriptorUUID) <- eserviceId.toFutureUUID.zip(descriptorId.toFutureUUID)
      eService                       <- catalogProcessService.getEServiceById(eserviceUUID)
      // This is an anti-pattern, but it's the cleanest solution
      _                              <- isTheProducer(eService, requesterId)
      descriptor                     <- eService.descriptors
        .find(_.id === descriptorUUID)
        .toFuture(EServiceDescriptorNotFound(eService.id.toString, descriptorId))
      attributes                     <- attributeRegistryManagementService
        .getBulkAttributes(extractIdsFromAttributes(eService.attributes))(contexts)
        .map(_.attributes)
      eServiceAttributes             <- eService.attributes.toApi(attributes)
      requesterTenant                <- tenantManagementService.getTenant(eService.producerId)
    } yield ProducerEServiceDescriptor(
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
      eservice = ProducerDescriptorEService(
        id = eService.id,
        name = eService.name,
        description = eService.description,
        technology = eService.technology.toApi,
        attributes = eServiceAttributes,
        descriptors = getNonDraftDescriptors(eService).map(_.toCompactDescriptor),
        draftDescriptor = getDraftDescriptor(eService).map(ad => CompactDescriptor(ad.id, ad.state.toApi, ad.version)),
        mail = requesterTenant.mails.find(_.kind == TenantManagement.MailKind.CONTACT_EMAIL).map(_.toApi)
      )
    )

    onComplete(result) {
      handleError(s"Error retrieving producer descriptor $descriptorId of eservice $eserviceId") orElse {
        case Failure(x: EServiceDescriptorNotFound) =>
          logger.warn(x.getMessage)
          getProducerEServiceDescriptor404(problemOf(StatusCodes.NotFound, x))
        case Failure(x: InvalidEServiceRequester)   =>
          logger.warn(x.getMessage)
          getProducerEServiceDescriptor403(problemOf(StatusCodes.Forbidden, x))
        case Success(descriptor)                    => getProducerEServiceDescriptor200(descriptor)
      }
    }

  }

  private def isTheProducer(eService: CatalogProcess.EService, requesterId: UUID): Future[Unit] =
    Future.failed(InvalidEServiceRequester(eService.id, requesterId)).unlessA(eService.producerId == requesterId)

  private def getActiveDescriptor(eService: CatalogProcess.EService): Option[CatalogProcess.EServiceDescriptor] =
    eService.descriptors
      .filter(d => ACTIVE_DESCRIPTOR_STATES_FILTER.contains(d.state))
      .sortBy(_.version.toInt)
      .lastOption

  private def getDraftDescriptor(eService: CatalogProcess.EService): Option[CatalogProcess.EServiceDescriptor] =
    eService.descriptors.find(_.state == CatalogProcess.EServiceDescriptorState.DRAFT)

  private def getNonDraftDescriptors(eService: CatalogProcess.EService): Seq[CatalogProcess.EServiceDescriptor] =
    eService.descriptors.filter(_.state != CatalogProcess.EServiceDescriptorState.DRAFT)

  override def suspendDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Unit] =
      catalogProcessService.suspendDescriptor(eServiceId, descriptorId)(contexts)
    onComplete(result) {
      handleError(s"Error suspending descriptor ${descriptorId}") orElse { case Success(_) =>
        suspendDescriptor204
      }
    }
  }

  private def getDocumentContentType(document: CatalogManagementDependency.EServiceDoc): Future[ContentType] =
    ContentType
      .parse(document.contentType)
      .fold(
        ex => Future.failed(ContentTypeParsingError(document.contentType, document.path, ex.map(_.formatPretty))),
        Future.successful
      )

  private def convertToMessageEntity(documentDetails: DocumentDetails): MessageEntity = {
    val randomPath: Path               = Files.createTempDirectory(s"document")
    val temporaryFilePath: String      = s"${randomPath.toString}/${documentDetails.name}"
    val file: File                     = new File(temporaryFilePath)
    val outputStream: FileOutputStream = new FileOutputStream(file)
    documentDetails.data.writeTo(outputStream)
    HttpEntity.fromFile(documentDetails.contentType, file)
  }

  override def getEServiceDocumentById(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerFile: ToEntityMarshaller[File]
  ): Route = {
    authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE) {
      val operationLabel =
        s"Retrieving EService document $documentId for EService $eServiceId and descriptor $descriptorId"
      logger.info(operationLabel)

      val result: Future[DocumentDetails] = for {
        document    <- catalogManagementService.getEServiceDocument(eServiceId, descriptorId, documentId)
        contentType <- getDocumentContentType(document)
        response    <- fileManager.get(ApplicationConfiguration.consumerDocumentsContainer)(document.path)
      } yield DocumentDetails(document.name, contentType, response)

      onComplete(result) {
        getEServiceDocumentByIdResponse[DocumentDetails](operationLabel) { documentDetails =>
          val output: MessageEntity = convertToMessageEntity(documentDetails)
          complete(output)
        }
      }
    }
  }
}
