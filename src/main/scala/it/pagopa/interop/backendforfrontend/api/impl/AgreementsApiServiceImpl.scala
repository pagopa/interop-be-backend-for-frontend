package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller

import akka.http.scaladsl.model.{ContentType, HttpEntity, MediaTypes, HttpHeader}
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.directives.FileInfo
import cats.syntax.all._
import it.pagopa.interop.commons.utils.AkkaUtils._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.attributeregistryprocess.client.{model => AttributeRegistry}
import it.pagopa.interop.backendforfrontend.api.AgreementsApiService
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.BFFErrors.{
  AgreementDescriptorNotFound,
  ContractNotFound,
  InvalidContentType
}
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service._
import it.pagopa.interop.backendforfrontend.service.types.AgreementProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.CatalogProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.TenantProcessServiceTypes._
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}

import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.service.UUIDSupplier
import it.pagopa.interop.tenantprocess.client.{model => TenantProcess}
import it.pagopa.interop.agreementprocess.client.{model => AgreementProcess}
import it.pagopa.interop.backendforfrontend.api.impl.Utils.isUpgradable
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.backendforfrontend.common.HeaderUtils._

import java.io.File
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class AgreementsApiServiceImpl(
  agreementProcessService: AgreementProcessService,
  attributeRegistryService: AttributeRegistryProcessService,
  catalogProcessService: CatalogProcessService,
  partyProcessService: PartyProcessService,
  tenantProcessService: TenantProcessService,
  fileManager: FileManager,
  uuidSupplier: UUIDSupplier
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
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error creating agreement for EService ${payload.eserviceId} and Descriptor ${payload.descriptorId}",
        headers
      ) orElse { case Success(resource) => createAgreement200(headers)(resource) }
    }
  }

  override def deleteAgreement(
    agreementId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    val result: Future[Unit] = agreementId.toFutureUUID >>= agreementProcessService.deleteAgreement

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error deleting agreement $agreementId", headers) orElse { case Success(_) =>
        deleteAgreement204(headers)
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
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving agreement $agreementId", headers) orElse { case Success(agreement) =>
        getAgreementById200(headers)(agreement)
      }
    }
  }

  override def getAgreements(
    offset: Int,
    limit: Int,
    eservicesIds: String,
    producersIds: String,
    consumersIds: String,
    states: String,
    showOnlyUpgradeable: Boolean
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreements],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val agreements: Future[Agreements] = for {
      producers    <- parseArrayParameters(producersIds).traverse(_.toFutureUUID)
      eservices    <- parseArrayParameters(eservicesIds).traverse(_.toFutureUUID)
      consumers    <- parseArrayParameters(consumersIds).traverse(_.toFutureUUID)
      states       <- parseArrayParameters(states).traverse(AgreementProcess.AgreementState.fromValue).toFuture
      pagedResults <- agreementProcessService.getAgreements(
        producersIds = producers,
        eservicesIds = eservices,
        consumersIds = consumers,
        states = states,
        offset = offset,
        limit = limit,
        showOnlyUpgradeable = showOnlyUpgradeable.some
      )
      agreements   <- Future.traverse(pagedResults.results)(enrichAgreements)
    } yield Agreements(
      results = agreements,
      pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
    )

    onComplete(agreements) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving agreements", headers) orElse { case Success(agreements) =>
        getAgreements200(headers)(agreements)
      }
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
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error activating agreement $agreementId", headers) orElse { case Success(agreement) =>
        activateAgreement200(headers)(agreement)
      }
    }
  }

  override def submitAgreement(agreementId: String, payload: AgreementSubmissionPayload)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = {
    val agreement: Future[Agreement] = for {
      agreementUuid <- agreementId.toFutureUUID
      agreement     <- agreementProcessService.submitAgreement(agreementUuid, payload.toSeed)
      apiAgreement  <- enhanceAgreement(agreement)
    } yield apiAgreement

    onComplete(agreement) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error submitting agreement $agreementId", headers) orElse { case Success(agreement) =>
        submitAgreement200(headers)(agreement)
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
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error suspending agreement $agreementId", headers) orElse { case Success(agreement) =>
        suspendAgreement200(headers)(agreement)
      }
    }
  }

  override def rejectAgreement(agreementId: String, payload: AgreementRejectionPayload)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = {
    val agreement: Future[Agreement] = for {
      agreementUuid <- agreementId.toFutureUUID
      agreement     <- agreementProcessService.rejectAgreement(agreementUuid, payload.toSeed)
      apiAgreement  <- enhanceAgreement(agreement)
    } yield apiAgreement

    onComplete(agreement) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error rejecting agreement $agreementId", headers) orElse { case Success(agreement) =>
        rejectAgreement200(headers)(agreement)
      }
    }
  }

  override def archiveAgreement(
    agreementId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    val result: Future[Unit] = for {
      agreementUuid <- agreementId.toFutureUUID
      _             <- agreementProcessService.archiveAgreement(agreementUuid)
    } yield ()

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error archiving agreement $agreementId", headers) orElse { case Success(_) =>
        archiveAgreement204(headers)
      }
    }
  }

  override def updateAgreement(agreementId: String, payload: AgreementUpdatePayload)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = {
    val agreement: Future[Agreement] = for {
      agreementUuid <- agreementId.toFutureUUID
      agreement     <- agreementProcessService.updateAgreement(agreementUuid, payload.toSeed)
      apiAgreement  <- enhanceAgreement(agreement)
    } yield apiAgreement

    onComplete(agreement) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error updating agreement $agreementId", headers) orElse { case Success(agreement) =>
        updateAgreement200(headers)(agreement)
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
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error upgrading agreement $agreementId", headers) orElse { case Success(agreement) =>
        upgradeAgreement200(headers)(agreement)
      }
    }
  }

  def parallelGet(agreement: AgreementProcess.Agreement)(implicit
    contexts: Seq[(String, String)]
  ): Future[(TenantProcess.Tenant, TenantProcess.Tenant, CatalogProcess.EService)] =
    tenantProcessService
      .getTenant(agreement.consumerId)
      .zip(tenantProcessService.getTenant(agreement.producerId))
      .zip(catalogProcessService.getEServiceById(agreement.eserviceId))
      .map({ case ((consumer, producer), eservice) =>
        (consumer, producer, eservice)
      })

  def enrichAgreements(
    agreement: AgreementProcess.Agreement
  )(implicit contexts: Seq[(String, String)]): Future[AgreementListEntry] = for {
    (consumerTenant, producerTenant, eService) <- parallelGet(agreement)
    currentDescriptor                          <- eService.descriptors
      .find(_.id == agreement.descriptorId)
      .toFuture(AgreementDescriptorNotFound(agreement.id))
  } yield AgreementListEntry(
    id = agreement.id,
    state = agreement.state.toApi,
    consumer =
      CompactOrganization(id = consumerTenant.id, name = consumerTenant.name, kind = consumerTenant.kind.map(_.toApi)),
    eservice = CompactEService(
      id = eService.id,
      name = eService.name,
      producer = CompactOrganization(producerTenant.id, producerTenant.name, producerTenant.kind.map(_.toApi))
    ),
    descriptor = currentDescriptor.toCompactDescriptor,
    canBeUpgraded = isUpgradable(currentDescriptor, eService.descriptors),
    suspendedByConsumer = agreement.suspendedByConsumer,
    suspendedByProducer = agreement.suspendedByProducer,
    suspendedByPlatform = agreement.suspendedByPlatform
  )

  def enhanceAgreement(
    agreement: AgreementProcess.Agreement
  )(implicit contexts: Seq[(String, String)]): Future[Agreement] = for {
    (consumerTenant, producerTenant, eService) <- parallelGet(agreement)
    currentDescriptor                          <- eService.descriptors
      .find(_.id == agreement.descriptorId)
      .toFuture(AgreementDescriptorNotFound(agreement.id))
    activeDescriptor           = eService.descriptors.sortBy(_.version.toInt).lastOption
    activeDescriptorAttributes = activeDescriptor.fold(Seq.empty[UUID])(descriptorAttributesIds)

    allAttributesIds = (activeDescriptorAttributes ++ Utils.tenantAttributesIds(consumerTenant)).distinct
    attributes <- attributeRegistryService.getBulkAttributes(allAttributesIds)

    agreementVerifiedAttrs  = filterAttributes(attributes, agreement.verifiedAttributes.map(_.id))
      .map(_.toVerifiedAttribute)
    agreementCertifiedAttrs = filterAttributes(attributes, agreement.certifiedAttributes.map(_.id))
      .map(_.toCertifiedAttribute)
    agreementDeclaredAttrs  = filterAttributes(attributes, agreement.declaredAttributes.map(_.id))
      .map(_.toDeclaredAttribute)

    tenantAttributes = Utils.enhanceTenantAttributes(consumerTenant.attributes, attributes)
  } yield Agreement(
    id = agreement.id,
    descriptorId = agreement.descriptorId,
    producer = CompactOrganization(
      id = agreement.producerId,
      name = producerTenant.name,
      kind = producerTenant.kind.map(_.toApi)
    ),
    consumer = Tenant(
      id = agreement.consumerId,
      selfcareId = consumerTenant.id.some,
      externalId = consumerTenant.externalId.toApi,
      createdAt = consumerTenant.createdAt,
      updatedAt = consumerTenant.updatedAt,
      name = consumerTenant.name,
      attributes = tenantAttributes,
      contactMail = consumerTenant.mails.find(_.kind == TenantProcess.MailKind.CONTACT_EMAIL).map(_.toApi),
      features = consumerTenant.features.map(_.toApi)
    ),
    eservice = AgreementsEService(
      id = agreement.eserviceId,
      name = eService.name,
      version = currentDescriptor.version,
      activeDescriptor = activeDescriptor.map(_.toCompactDescriptor)
    ),
    state = agreement.state.toApi,
    verifiedAttributes = agreementVerifiedAttrs,
    certifiedAttributes = agreementCertifiedAttrs,
    declaredAttributes = agreementDeclaredAttrs,
    suspendedByConsumer = agreement.suspendedByConsumer,
    suspendedByProducer = agreement.suspendedByProducer,
    suspendedByPlatform = agreement.suspendedByPlatform,
    isContractPresent = agreement.contract.nonEmpty,
    consumerNotes = agreement.consumerNotes,
    rejectionReason = agreement.rejectionReason,
    consumerDocuments = agreement.consumerDocuments.map(_.toApi),
    createdAt = agreement.createdAt,
    updatedAt = agreement.updatedAt,
    suspendedAt = agreement.suspendedAt
  )

  def descriptorAttributesIds(descriptor: CatalogProcess.EServiceDescriptor): Seq[UUID] = {
    (descriptor.attributes.verified.flatten ++ descriptor.attributes.declared.flatten ++ descriptor.attributes.certified.flatten)
      .map(_.id)
  }

  def filterAttributes(
    registryAttributes: Seq[AttributeRegistry.Attribute],
    filterIds: Seq[UUID]
  ): Seq[AttributeRegistry.Attribute] =
    filterIds.flatMap(id => registryAttributes.find(_.id == id))

  override def addAgreementConsumerDocument(
    name: String,
    prettyName: String,
    doc: (FileInfo, File),
    agreementId: String
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerFile: ToEntityMarshaller[File]
  ): Route = {
    logger.info(s"Adding consumer document to agreement $agreementId")

    val documentId: UUID         = uuidSupplier.get()
    val documentPath: String     = s"${ApplicationConfiguration.consumerDocumentsPath}/$agreementId"
    val result: Future[Document] =
      for {
        agreementUUID <- agreementId.toFutureUUID
        seed          <- fileManager
          .store(ApplicationConfiguration.consumerDocumentsContainer, documentPath)(documentId.toString, doc)
          .map(path =>
            AgreementProcess.DocumentSeed(
              id = documentId,
              name = name,
              prettyName = prettyName,
              contentType = doc._1.contentType.toString(),
              path = path
            )
          )
        document      <- agreementProcessService.addConsumerDocument(agreementUUID, seed)
      } yield document.toApi

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error Adding consumer document to agreement $agreementId", headers) orElse {
        case Success(contract) =>
          complete(StatusCodes.OK, headers, contract)
      }
    }
  }

  override def getAgreementConsumerDocument(agreementId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerFile: ToEntityMarshaller[File]
  ): Route = {
    logger.info(s"Retrieving consumer document $documentId from agreement $agreementId")

    val result: Future[HttpEntity.Strict] =
      for {
        agreementUUID <- agreementId.toFutureUUID
        documentUUID  <- documentId.toFutureUUID
        document      <- agreementProcessService.getConsumerDocument(agreementUUID, documentUUID)
        contentType   <- getMediaType(document.contentType, agreementId, documentId)
        byteStream    <- fileManager.get(ApplicationConfiguration.consumerDocumentsContainer)(document.path)
      } yield HttpEntity(contentType, byteStream.toByteArray())

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error downloading contract fro agreement $agreementId", headers) orElse { case Success(contract) =>
        complete(StatusCodes.OK, headers, contract)
      }
    }
  }

  private def getMediaType(contentType: String, agreementId: String, documentId: String): Future[ContentType] =
    ContentType
      .parse(contentType)
      .leftMap(errors => InvalidContentType(contentType, agreementId, documentId, errors))
      .toFuture

  override def getAgreementContract(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerFile: ToEntityMarshaller[File]
  ): Route = {
    logger.info(s"Retrieving contract for agreement $agreementId")

    val result: Future[HttpEntity.Strict] =
      for {
        uuid       <- agreementId.toFutureUUID
        agreement  <- agreementProcessService.getAgreementById(uuid)
        contract   <- agreement.contract.toFuture(ContractNotFound(agreementId))
        byteStream <- fileManager.get(ApplicationConfiguration.consumerDocumentsContainer)(contract.path)
      } yield HttpEntity(ContentType(MediaTypes.`application/pdf`), byteStream.toByteArray())

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error downloading contract fro agreement $agreementId", headers) orElse { case Success(contract) =>
        complete(StatusCodes.OK, headers, contract)
      }
    }
  }

  override def removeAgreementConsumerDocument(agreementId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Deleting consumer document $documentId for agreement $agreementId")

    val result: Future[Unit] =
      for {
        agreementUUID <- agreementId.toFutureUUID
        documentUUID  <- documentId.toFutureUUID
        result        <- agreementProcessService.removeConsumerDocument(agreementUUID, documentUUID)
      } yield result

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error deleting consumer document $documentId for agreement $agreementId", headers) orElse {
        case Success(_) => removeAgreementConsumerDocument204(headers)
      }
    }
  }

  override def cloneAgreement(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {
    logger.info(s"Cloning agreement $agreementId")

    val result: Future[CreatedResource] = for {
      agreementUuid <- agreementId.toFutureUUID
      result        <- agreementProcessService.cloneAgreement(agreementUuid)
    } yield CreatedResource(result.id)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error cloning agreement $agreementId", headers) orElse { case Success(resource) =>
        cloneAgreement200(headers)(resource)
      }
    }
  }

  override def getAgreementEServiceProducers(q: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerCompactAgreementEServices: ToEntityMarshaller[CompactEServicesLight],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(
      s"Retrieving producer eservices from agreement filtered by eservice name $q, offset $offset, limit $limit"
    )

    def emptyResponse: Future[CompactEServicesLight] = Future.successful(
      CompactEServicesLight(results = Nil, pagination = Pagination(offset = offset, limit = limit, totalCount = 0))
    )

    def validResponse: Future[CompactEServicesLight] = for {
      requesterId  <- getOrganizationIdFutureUUID(contexts)
      pagedResults <- agreementProcessService.getAgreementEServices(
        eServiceName = q,
        producersIds = Seq(requesterId),
        consumersIds = Seq.empty,
        limit = limit,
        offset = offset
      )
    } yield CompactEServicesLight(
      results = pagedResults.results.map(t => CompactEServiceLight(id = t.id, name = t.name)),
      pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
    )

    val result = q.filterNot(_.isBlank) match {
      case Some(value) if value.length < 3 => emptyResponse
      case _                               => validResponse
    }

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error retrieving eservices from agreement filtered by eservice name $q, offset $offset, limit $limit",
        headers
      ) orElse { case Success(producers) =>
        getAgreementEServiceProducers200(headers)(producers)
      }
    }
  }

  def getAgreementEServiceConsumers(q: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerCompactEServicesLight: ToEntityMarshaller[CompactEServicesLight],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(
      s"Retrieving consumer eservices from agreement filtered by eservice name $q, offset $offset, limit $limit"
    )

    def emptyResponse: Future[CompactEServicesLight] = Future.successful(
      CompactEServicesLight(results = Nil, pagination = Pagination(offset = offset, limit = limit, totalCount = 0))
    )

    def validResponse: Future[CompactEServicesLight] = for {
      requesterId  <- getOrganizationIdFutureUUID(contexts)
      pagedResults <- agreementProcessService.getAgreementEServices(
        eServiceName = q,
        producersIds = Seq.empty,
        consumersIds = Seq(requesterId),
        limit = limit,
        offset = offset
      )
    } yield CompactEServicesLight(
      results = pagedResults.results.map(t => CompactEServiceLight(id = t.id, name = t.name)),
      pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
    )

    val result = q.filterNot(_.isBlank) match {
      case Some(value) if value.length < 3 => emptyResponse
      case _                               => validResponse
    }

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error retrieving eservices from agreement filtered by eservice name $q, offset $offset, limit $limit",
        headers
      ) orElse { case Success(consumers) =>
        getAgreementEServiceConsumers200(headers)(consumers)
      }
    }
  }

  override def getAgreementProducers(q: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCompactOrganizations: ToEntityMarshaller[CompactOrganizations]
  ): Route = {

    def emptyResponse: Future[CompactOrganizations] = Future.successful(
      CompactOrganizations(results = Nil, pagination = Pagination(offset = offset, limit = limit, totalCount = 0))
    )

    def validResponse: Future[CompactOrganizations] =
      agreementProcessService
        .getAgreementProducers(q, offset = offset, limit = limit)
        .map(pagedResults =>
          CompactOrganizations(
            results = pagedResults.results
              .map(t => CompactOrganization(id = t.id, name = t.name, kind = None)), // TODO Da gestire successivamente
            pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
          )
        )

    val result = q.filterNot(_.isBlank) match {
      case Some(value) if value.length < 3 => emptyResponse
      case _                               => validResponse
    }

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error retrieving producers from agreement filtered by producer name $q, offset $offset, limit $limit",
        headers
      ) orElse { case Success(producers) =>
        getAgreementProducers200(headers)(producers)
      }
    }
  }

  def getAgreementConsumers(q: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCompactOrganizations: ToEntityMarshaller[CompactOrganizations]
  ): Route = {

    def emptyResponse: Future[CompactOrganizations] = Future.successful(
      CompactOrganizations(results = Nil, pagination = Pagination(offset = offset, limit = limit, totalCount = 0))
    )

    def validResponse: Future[CompactOrganizations] =
      agreementProcessService
        .getAgreementConsumers(q, offset = offset, limit = limit)
        .map(pagedResults =>
          CompactOrganizations(
            results = pagedResults.results.map(t => CompactOrganization(id = t.id, name = t.name)),
            pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
          )
        )

    val result = q.filterNot(_.isBlank) match {
      case Some(value) if value.length < 3 => emptyResponse
      case _                               => validResponse
    }

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error retrieving consumers from agreement filtered by consumer name $q, offset $offset, limit $limit",
        headers
      ) orElse { case Success(consumers) =>
        getAgreementConsumers200(headers)(consumers)
      }
    }
  }
}
