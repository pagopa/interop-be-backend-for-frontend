package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{ContentType, HttpEntity, MediaTypes}
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.attributeregistrymanagement.client.{model => AttributeRegistry}
import it.pagopa.interop.backendforfrontend.api.AgreementsApiService
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.BFFErrors.{
  AgreementDescriptorNotFound,
  ContractNotFound,
  InvalidContentType,
  InvalidQueryParameter
}
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service._
import it.pagopa.interop.backendforfrontend.service.types.AgreementProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.CatalogManagementServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.TenantManagementServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.TenantManagementServiceTypes.AdaptableTenantAttribute._
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagement}
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.service.UUIDSupplier
import it.pagopa.interop.tenantmanagement.client.{model => TenantManagement}
import it.pagopa.interop.agreementprocess.client.{model => AgreementProcess}
import it.pagopa.interop.backendforfrontend.api.impl.Utils
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters

import java.io.File
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class AgreementsApiServiceImpl(
  agreementProcessService: AgreementProcessService,
  attributeRegistryService: AttributeRegistryManagementService,
  catalogManagementService: CatalogManagementService,
  partyProcessService: PartyProcessService,
  tenantManagementService: TenantManagementService,
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
      handleError(
        s"Error creating agreement for EService ${payload.eserviceId} and Descriptor ${payload.descriptorId}"
      ) orElse { case Success(resource) => createAgreement200(resource) }
    }
  }

  override def deleteAgreement(
    agreementId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    val result: Future[Unit] = agreementId.toFutureUUID >>= agreementProcessService.deleteAgreement

    onComplete(result) {
      handleError(s"Error deleting agreement $agreementId") orElse { case Success(_) => deleteAgreement204 }
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
      handleError(s"Error rejecting agreement $agreementId") orElse { case Success(agreement) =>
        rejectAgreement200(agreement)
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
      handleError(s"Error updating agreement $agreementId") orElse { case Success(agreement) =>
        updateAgreement200(agreement)
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

  def parallelGet(agreement: AgreementProcess.Agreement)(implicit
    contexts: Seq[(String, String)]
  ): Future[(TenantManagement.Tenant, TenantManagement.Tenant, CatalogManagement.EService)] =
    tenantManagementService
      .getTenant(agreement.consumerId)
      .zip(tenantManagementService.getTenant(agreement.producerId))
      .zip(catalogManagementService.getEService(agreement.eserviceId))
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
    consumer = CompactOrganization(consumerTenant.id, consumerTenant.name),
    eservice = CompactEService(
      id = eService.id,
      name = eService.name,
      producer = CompactOrganization(producerTenant.id, producerTenant.name)
    ),
    descriptor = currentDescriptor.toCompactDescriptor,
    canBeUpgraded = isUpgradable(currentDescriptor, eService.descriptors),
    suspendedByConsumer = agreement.suspendedByConsumer,
    suspendedByProducer = agreement.suspendedByProducer,
    suspendedByPlatform = agreement.suspendedByPlatform
  )

  def isUpgradable(
    descriptor: CatalogManagement.EServiceDescriptor,
    descriptors: Seq[CatalogManagement.EServiceDescriptor]
  ): Boolean =
    descriptors
      .filter(_.version.toInt > descriptor.version.toInt)
      .exists(d =>
        d.state == CatalogManagement.EServiceDescriptorState.PUBLISHED ||
          d.state == CatalogManagement.EServiceDescriptorState.SUSPENDED
      )

  def enhanceAgreement(
    agreement: AgreementProcess.Agreement
  )(implicit contexts: Seq[(String, String)]): Future[Agreement] = for {
    (consumerTenant, producerTenant, eService) <- parallelGet(agreement)
    currentDescriptor                          <- eService.descriptors
      .find(_.id == agreement.descriptorId)
      .toFuture(AgreementDescriptorNotFound(agreement.id))
    activeDescriptor = eService.descriptors.sortBy(_.version.toInt).lastOption

    allAttributesIds = (eServiceAttributesIds(eService) ++ Utils.tenantAttributesIds(consumerTenant)).distinct
    attributes <- attributeRegistryService.getBulkAttributes(allAttributesIds)

    agreementVerifiedAttrs  = filterAttributes(attributes, agreement.verifiedAttributes.map(_.id))
      .map(_.toVerifiedAttribute)
    agreementCertifiedAttrs = filterAttributes(attributes, agreement.certifiedAttributes.map(_.id))
      .map(_.toCertifiedAttribute)
    agreementDeclaredAttrs  = filterAttributes(attributes, agreement.declaredAttributes.map(_.id))
      .map(_.toDeclaredAttribute)

    tenantAttributes = Utils.enhanceTenantAttributes(consumerTenant.attributes, attributes.attributes)
  } yield Agreement(
    id = agreement.id,
    descriptorId = agreement.descriptorId,
    producer = CompactTenant(id = agreement.producerId, name = producerTenant.name),
    consumer = Tenant(
      id = agreement.consumerId,
      selfcareId = consumerTenant.id.some,
      externalId = consumerTenant.externalId.toApi,
      createdAt = consumerTenant.createdAt,
      updatedAt = consumerTenant.updatedAt,
      name = consumerTenant.name,
      attributes = tenantAttributes,
      contactMail = consumerTenant.mails.find(_.kind == TenantManagement.MailKind.CONTACT_EMAIL).map(_.toApi)
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
    consumerNotes = agreement.consumerNotes,
    rejectionReason = agreement.rejectionReason,
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

  def filterAttributes(
    registryAttributes: MgmtAttributesResponse,
    filterIds: Seq[UUID]
  ): Seq[AttributeRegistry.Attribute] =
    filterIds.flatMap(id => registryAttributes.attributes.find(_.id == id))

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
    val documentPath: String     = s"${ApplicationConfiguration.consumerDocumentsPath}/$agreementId/$documentId"
    val result: Future[Document] =
      for {
        agreementUUID <- agreementId.toFutureUUID
        seed          <- fileManager
          .store(ApplicationConfiguration.consumerDocumentsContainer, documentPath)(doc._1.fileName, doc)
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
      handleError(s"Error Adding consumer document to agreement $agreementId") orElse { case Success(contract) =>
        complete(contract)
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
      handleError(s"Error downloading contract fro agreement $agreementId") orElse { case Success(contract) =>
        complete(contract)
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
      handleError(s"Error downloading contract fro agreement $agreementId") orElse { case Success(contract) =>
        complete(contract)
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
      handleError(s"Error deleting consumer document $documentId for agreement $agreementId") orElse {
        case Success(_) => removeAgreementConsumerDocument204
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
      handleError(s"Error cloning agreement $agreementId") orElse { case Success(resource) =>
        cloneAgreement200(resource)
      }
    }
  }

  override def getAgreementProducers(q: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCompactOrganizations: ToEntityMarshaller[CompactOrganizations]
  ): Route = {
    val result: Future[CompactOrganizations] =
      for {
        _            <- validateQueryName(q)
        pagedResults <- agreementProcessService.getAgreementProducers(q, offset = offset, limit = limit)
      } yield CompactOrganizations(
        results = pagedResults.results.map(t => CompactOrganization(id = t.id, name = t.name)),
        pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
      )

    onComplete(result) {
      handleError(
        s"Error retrieving producers from agreement filtered by producer name $q, offset $offset, limit $limit"
      ) orElse {
        case Failure(_: InvalidQueryParameter) =>
          getAgreementProducers200(
            CompactOrganizations(results = Nil, pagination = Pagination(offset = offset, limit = limit, totalCount = 0))
          )
        case Success(producers)                =>
          getAgreementProducers200(producers)
      }
    }
  }

  private def validateQueryName(q: Option[String]): Future[Unit] = {
    q match {
      case Some(value) if value.length < 3 => Future.failed(InvalidQueryParameter(value))
      case _                               => Future.successful(())
    }
  }
}
