package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{ContentType, HttpEntity, MessageEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import io.circe.Json
import it.pagopa.interop.agreementprocess.client.{model => AgreementProcess}
import it.pagopa.interop.agreementprocess.lifecycle.AttributesRules.certifiedAttributesSatisfied
import it.pagopa.interop.backendforfrontend.api.EservicesApiService
import it.pagopa.interop.backendforfrontend.common.system.{ApplicationConfiguration, FileManagerUtils}
import it.pagopa.interop.backendforfrontend.error.BFFErrors._
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service._
import it.pagopa.interop.backendforfrontend.service.types.AgreementProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.CatalogProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.TenantManagementServiceTypes._
import it.pagopa.interop.catalogprocess.client.model.EServices
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}
import CatalogProcess.EServiceDescriptorState.DRAFT
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.parser.{InterfaceParser, InterfaceParserUtils}
import it.pagopa.interop.commons.utils.AkkaUtils._
import it.pagopa.interop.commons.utils.Digester
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.service.UUIDSupplier
import it.pagopa.interop.tenantmanagement.client.{model => TenantManagement}

import java.io.{ByteArrayOutputStream, File, FileOutputStream}
import java.nio.file.{Files, Path}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.xml.Elem
import java.time.OffsetDateTime

final case class EServicesApiServiceImpl(
  agreementProcessService: AgreementProcessService,
  attributeRegistryManagementService: AttributeRegistryManagementService,
  catalogProcessService: CatalogProcessService,
  tenantManagementService: TenantManagementService,
  partyProcessService: PartyProcessService,
  fileManager: FileManager,
  uuidSupplier: UUIDSupplier
)(implicit ec: ExecutionContext)
    extends EservicesApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private lazy val INTERFACE = "INTERFACE"
  private lazy val DOCUMENT  = "DOCUMENT"

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

  override def updateDraftDescriptor(
    eServiceId: String,
    descriptorId: String,
    updateEServiceDescriptorSeed: UpdateEServiceDescriptorSeed
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {
    val result = for {
      eServiceIdUUID   <- eServiceId.toFutureUUID
      descriptorIdUUID <- descriptorId.toFutureUUID
      descriptor       <- catalogProcessService
        .updateDraftDescriptor(eServiceIdUUID, descriptorIdUUID, updateEServiceDescriptorSeed.toProcess)(contexts)
    } yield descriptor.toApi

    onComplete(result) {
      handleError(
        s"Error updating draft descriptor $descriptorId on service $eServiceId with seed: $updateEServiceDescriptorSeed"
      ) orElse { case Success(resource) =>
        updateDraftDescriptor200(resource)
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
      agreement                      <- getLatestAgreement(requesterId, eService)
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

    def getResults(producerId: UUID, eServicesIds: List[UUID]): Future[EServices] = catalogProcessService.getEServices(
      name = q,
      eServicesIds = eServicesIds,
      producersIds = List(producerId),
      states = Nil,
      offset = offset,
      limit = limit
    )

    val result: Future[ProducerEServices] = for {
      producerId    <- getOrganizationIdFutureUUID(contexts)
      consumerUUIDs <- Future.traverse(parseArrayParameters(consumersIds))(_.toFutureUUID)
      pagedResults  <-
        if (consumerUUIDs.isEmpty) getResults(producerId, Nil)
        else
          getProducerEServicesIds(producerId, consumerUUIDs).flatMap {
            case Nil => Future.successful(EServices(Nil, 0))
            case xs  => getResults(producerId, xs)
          }
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

  private def getProducerEServicesIds(producerId: UUID, consumersUUIDs: List[UUID])(implicit
    contexts: Seq[(String, String)]
  ): Future[List[UUID]] =
    getAllAgreements(
      producersIds = producerId :: Nil,
      consumersIds = consumersUUIDs,
      eServicesIds = Nil,
      states = List(AgreementProcess.AgreementState.ACTIVE, AgreementProcess.AgreementState.SUSPENDED)
    ).map(_.map(_.eserviceId).distinct)

  private def getAllAgreements(
    producersIds: List[UUID],
    consumersIds: List[UUID],
    eServicesIds: List[UUID],
    states: List[AgreementProcess.AgreementState]
  )(implicit contexts: Seq[(String, String)]): Future[List[AgreementProcess.Agreement]] = {

    def getAgreementsFrom(offset: Int): Future[List[AgreementProcess.Agreement]] =
      agreementProcessService
        .getAgreements(
          producersIds = producersIds,
          consumersIds = consumersIds,
          eservicesIds = eServicesIds,
          states = states,
          limit = 50,
          offset = offset
        )
        .map(_.results.toList)

    def go(start: Int)(as: List[AgreementProcess.Agreement]): Future[List[AgreementProcess.Agreement]] =
      getAgreementsFrom(start).flatMap(agrs =>
        if (agrs.size < 50) Future.successful(as ++ agrs) else go(start + 50)(as ++ agrs)
      )

    go(0)(Nil)
  }

  private def getLatestAgreement(requesterId: UUID, eService: CatalogProcess.EService)(implicit
    contexts: Seq[(String, String)]
  ): Future[Option[AgreementProcess.Agreement]] = {

    val ordering: Ordering[(Int, OffsetDateTime)] =
      Ordering.Tuple2(Ordering.Int.reverse, Ordering.by[OffsetDateTime, Long](_.toEpochSecond).reverse)

    getAllAgreements(
      consumersIds = requesterId :: Nil,
      eServicesIds = eService.id :: Nil,
      producersIds = Nil,
      states = Nil
    ).map(
      _.map(agreement => (agreement, eService.descriptors.find(_.id == agreement.descriptorId)))
        .collect { case (agreement, Some(descriptor)) => (agreement, descriptor) }
        .sortBy(s => (s._2.version.toInt, s._1.createdAt))(ordering)
        .headOption
        .map(_._1)
    )
  }

  private def enhanceCatalogEService(
    requesterId: UUID
  )(eService: CatalogProcess.EService)(implicit contexts: Seq[(String, String)]): Future[CatalogEService] = for {
    producerTenant  <- tenantManagementService.getTenant(eService.producerId)
    requesterTenant <-
      if (requesterId != eService.producerId) tenantManagementService.getTenant(requesterId)
      else Future.successful(producerTenant)

    activeDescriptor = getActiveDescriptor(eService)
    latestAgreement <- getLatestAgreement(requesterId, eService)
  } yield CatalogEService(
    id = eService.id,
    name = eService.name,
    description = eService.description,
    producer = CompactOrganization(id = eService.producerId, name = producerTenant.name),
    agreement = latestAgreement.map(a => CompactAgreement(id = a.id, state = a.state.toApi)),
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

  override def createEServiceDocument(
    kind: String,
    prettyName: String,
    doc: (FileInfo, File),
    eServiceId: String,
    descriptorId: String
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {

    val isInterface: Boolean = kind match {
      case INTERFACE => true
      case DOCUMENT  => false
      case _         => false
    }

    def extractServerUrls(bytes: Array[Byte], isInterface: Boolean): Either[Throwable, List[String]] = if (isInterface)
      InterfaceParser.parseOpenApi(bytes).flatMap(InterfaceParserUtils.getUrls[Json]) orElse
        InterfaceParser.parseWSDL(bytes).flatMap(InterfaceParserUtils.getUrls[Elem])
    else Right(List.empty)

    val documentIdUuid: UUID = uuidSupplier.get()

    val result: Future[CreatedResource] = for {
      (eserviceUUID, descriptorUUID) <- eServiceId.toFutureUUID.zip(descriptorId.toFutureUUID)
      eService                       <- catalogProcessService.getEServiceById(eserviceUUID)
      _                              <- eService.descriptors
        .find(_.id === descriptorUUID)
        .toFuture(EServiceDescriptorNotFound(eService.id.toString, descriptorId))
      _                              <- FileManagerUtils.verify(doc, eService, isInterface).toFuture
      serverUrls                     <- extractServerUrls(Files.readAllBytes(doc._2.toPath), isInterface).toFuture
      filePath                       <- fileManager.store(
        ApplicationConfiguration.eServiceDocumentsContainer,
        ApplicationConfiguration.eServiceDocumentsPath
      )(documentIdUuid.toString, doc)
      _                              <- catalogProcessService
        .createEServiceDocument(
          eServiceId = eserviceUUID,
          descriptorId = descriptorUUID,
          documentSeed = CatalogProcess.CreateEServiceDescriptorDocumentSeed(
            documentId = documentIdUuid,
            prettyName = prettyName,
            fileName = doc._1.getFileName,
            filePath = filePath,
            kind = kind.toProcess,
            contentType = doc._1.getContentType.toString(),
            checksum = Digester.toMD5(doc._2),
            serverUrls = serverUrls
          )
        )(contexts)
    } yield CreatedResource(documentIdUuid)

    onComplete(result) {
      handleError(
        s"Error creating eService document of kind $kind and name $prettyName for eService $eServiceId and descriptor $descriptorId"
      ) orElse { case Success(document) =>
        createEServiceDocument200(document)
      }
    }
  }

  override def updateEServiceDocumentById(
    eServiceId: String,
    descriptorId: String,
    documentId: String,
    updateEServiceDescriptorDocumentSeed: UpdateEServiceDescriptorDocumentSeed
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServiceDoc: ToEntityMarshaller[EServiceDoc],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[EServiceDoc] = catalogProcessService
      .updateEServiceDocumentById(
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        documentId = documentId,
        updateEServiceDescriptorDocumentSeed = updateEServiceDescriptorDocumentSeed.toProcess
      )(contexts)
      .map(_.toApi)

    onComplete(result) {
      handleError(s"Error updating document $documentId on eService $eServiceId for descriptor $descriptorId") orElse {
        case Success(eServiceDoc) =>
          updateEServiceDocumentById200(eServiceDoc)
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
      handleError(s"Error suspending descriptor $descriptorId") orElse { case Success(_) =>
        suspendDescriptor204
      }
    }
  }

  override def cloneEServiceByDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedEServiceDescriptor]
  ): Route = {
    val result: Future[CreatedEServiceDescriptor] = for {
      eServiceIdUUID   <- eServiceId.toFutureUUID
      descriptorIdUUID <- descriptorId.toFutureUUID
      eservice         <- catalogProcessService
        .cloneEServiceByDescriptor(eServiceIdUUID, descriptorIdUUID)
      descriptorId     <- eservice.descriptors.headOption.map(_.id).toFuture(NoDescriptorInEservice(eServiceIdUUID))
    } yield eservice.toApiWithDescriptorId(descriptorId)

    onComplete(result) {
      handleError(s"Error cloning EService ${eServiceId} with descriptor ${descriptorId}") orElse {
        case Success(eservice) =>
          cloneEServiceByDescriptor200(eservice)
      }
    }
  }

  override def deleteEServiceDocumentById(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Unit] =
      catalogProcessService.deleteEServiceDocumentById(eServiceId, descriptorId, documentId)(contexts)
    onComplete(result) {
      handleError(s"Error deleting document $documentId for eService $eServiceId descriptor $descriptorId") orElse {
        case Success(_) => deleteEServiceDocumentById204
      }
    }
  }

  override def updateEServiceById(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {
    val result: Future[CreatedResource] =
      catalogProcessService.updateEServiceById(eServiceId, updateEServiceSeed.toProcess)(contexts).map(_.toApi)

    onComplete(result) {
      handleError(s"Error updating eservice with Id: $eServiceId") orElse { case Success(eservice) =>
        updateEServiceById200(eservice)
      }
    }
  }

  private def getDocumentContentType(document: CatalogProcess.EServiceDoc): Future[ContentType] =
    ContentType
      .parse(document.contentType)
      .fold(
        ex => Future.failed(ContentTypeParsingError(document.contentType, document.path, ex.map(_.formatPretty))),
        Future.successful
      )

  private def convertToMessageEntity(
    name: String,
    contentType: ContentType,
    data: ByteArrayOutputStream
  ): Future[MessageEntity] =
    Future {
      val randomPath: Path               = Files.createTempDirectory(s"document")
      val temporaryFilePath: String      = s"${randomPath.toString}/${name}"
      val file: File                     = new File(temporaryFilePath)
      val outputStream: FileOutputStream = new FileOutputStream(file)
      data.writeTo(outputStream)
      HttpEntity.fromFile(contentType, file)
    }

  override def getEServiceDocumentById(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServiceDoc: ToEntityMarshaller[EServiceDoc],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[MessageEntity] = for {
      document      <- catalogProcessService.getEServiceDocumentById(eServiceId, descriptorId, documentId)
      contentType   <- getDocumentContentType(document)
      response      <- fileManager.get(ApplicationConfiguration.eServiceDocumentsContainer)(document.path)
      messageEntity <- convertToMessageEntity(document.name, contentType, response)
    } yield messageEntity

    onComplete(result) {
      handleError(s"Error getting document $documentId of eservice $eServiceId") orElse {
        case Success(documentEntity) =>
          complete(documentEntity)
      }
    }
  }

  override def deleteEService(
    eServiceId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {

    /*
      Eservice = [Draft, Active+] -> Eservice = [Active+]
      EService = [Draft]          -> NOEservice
      EService = [Active+]        -> Errore (EService = [Active+])
      EService = []               -> NOEservice
     */

    def deleteEserviceIfEmpty(eService: CatalogProcessEService): Future[Boolean] = {
      if (eService.descriptors.forall(_.state == DRAFT))
        catalogProcessService.deleteEService(eService.id).map(_ => true)
      else Future.successful(false)
    }

    val result: Future[Unit] = for {
      eServiceUUID <- eServiceId.toFutureUUID
      eService     <- catalogProcessService.getEServiceById(eServiceUUID)
      maybeDraftDescriptor = getDraftDescriptor(eService)
      _   <-  maybeDraftDescriptor.fold(Future.unit)(draftDescriptor =>
        catalogProcessService.deleteDraft(eServiceUUID, draftDescriptor.id)
      )
//      _         <- Future.traverse(maybeDraftDescriptor.toList)(draftDescriptor =>
//        catalogProcessService.deleteDraft(eServiceUUID, draftDescriptor.id)
//      )
      isDeleted <- deleteEserviceIfEmpty(eService)
      _         <-
        if (!isDeleted && maybeDraftDescriptor.isEmpty) Future.failed(EServiceCannotBeDeleted(eService.id.toString))
        else Future.successful(())
    } yield ()

    onComplete(result) {
      handleError(s"Error deleting eservice with Id: $eServiceId") orElse { case Success(_) => deleteEService204 }
    }
  }

}
