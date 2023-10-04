package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{ContentType, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.authorizationprocess.client.model.ClientWithKeys
import it.pagopa.interop.backendforfrontend.api.PurposesApiService
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.BFFErrors._
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service._
import it.pagopa.interop.backendforfrontend.service.types.PurposeProcessServiceTypes._
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getOrganizationIdFutureUUID
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.purposeprocess.client.{model => PurposeProcess}
import it.pagopa.interop.tenantprocess.client.{model => TenantProcess}
import it.pagopa.interop.backendforfrontend.common.HeaderUtils._

import java.io.File
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class PurposesApiServiceImpl(
  catalogProcessService: CatalogProcessService,
  purposeProcessService: PurposeProcessService,
  tenantProcessService: TenantProcessService,
  agreementProcessService: AgreementProcessService,
  authorizationProcessService: AuthorizationProcessService,
  fileManager: FileManager
)(implicit ec: ExecutionContext)
    extends PurposesApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getConsumerPurposes(
    q: Option[String],
    eServicesIds: String,
    consumersIds: String,
    producersIds: String,
    states: String,
    offset: Int,
    limit: Int
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurposes: ToEntityMarshaller[Purposes],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(
      s"Retrieving Purposes for name $q, EServices $eServicesIds, Consumers $consumersIds offset $offset, limit $limit"
    )

    val result: Future[Purposes] =
      getPurposes(q, eServicesIds, consumersIds, producersIds, states, offset, limit, false)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error retrieving Purposes for name $q, EServices $eServicesIds, Consumers $consumersIds offset $offset, limit $limit",
        headers
      ) orElse { case Success(r) => getConsumerPurposes200(headers)(r) }
    }
  }

  override def getProducerPurposes(
    q: Option[String],
    eServicesIds: String,
    consumersIds: String,
    producersIds: String,
    states: String,
    offset: Int,
    limit: Int
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurposes: ToEntityMarshaller[Purposes],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Purposes] = getPurposes(q, eServicesIds, consumersIds, producersIds, states, offset, limit, true)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error retrieving Purposes for name $q, EServices $eServicesIds, Consumers $consumersIds offset $offset, limit $limit",
        headers
      ) orElse { case Success(r) => getProducerPurposes200(headers)(r) }
    }
  }

  private def getPurposes(
    q: Option[String],
    eServicesIds: String,
    consumersIds: String,
    producersIds: String,
    states: String,
    offset: Int,
    limit: Int,
    isExcludeDraft: Boolean
  )(implicit contexts: Seq[(String, String)]): Future[Purposes] = for {
    statesEnum     <- parseArrayParameters(states).distinct
      .traverse(PurposeProcess.PurposeVersionState.fromValue)
      .toFuture
    eServicesUUIDs <- parseArrayParameters(eServicesIds).distinct.traverse(_.toFutureUUID)
    consumersUUIDs <- parseArrayParameters(consumersIds).distinct.traverse(_.toFutureUUID)
    producersUUIDs <- parseArrayParameters(producersIds).distinct.traverse(_.toFutureUUID)
    pagedResults   <- purposeProcessService.getPurposes(
      name = q,
      eServicesIds = eServicesUUIDs,
      consumersIds = consumersUUIDs,
      producersIds = producersUUIDs,
      states = statesEnum,
      excludeDraft = isExcludeDraft.some,
      offset = offset,
      limit = limit
    )
    actualEServicesIds = pagedResults.results.map(_.eserviceId).distinct
    actualConsumersIds = pagedResults.results.map(_.consumerId).distinct
    eServices       <- actualEServicesIds.traverse(catalogProcessService.getEServiceById)
    producers       <- eServices.map(_.producerId).distinct.traverse(tenantProcessService.getTenant)
    consumers       <- actualConsumersIds.traverse(tenantProcessService.getTenant)
    enhancedResults <- pagedResults.results.traverse(enhancePurpose(_, eServices, producers, consumers))
  } yield Purposes(
    results = enhancedResults,
    pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
  )

  override def archivePurposeVersion(purposeId: String, versionId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurposeVersionResource: ToEntityMarshaller[PurposeVersionResource],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Archiving purpose $purposeId with version $versionId")

    val result: Future[PurposeVersionResource] = for {
      purposeUuid <- purposeId.toFutureUUID
      versionUuid <- versionId.toFutureUUID
      _           <- purposeProcessService.archivePurposeVersion(purposeUuid, versionUuid)
      _           <- authorizationProcessService.removePurposeFromClients(purposeUuid)
    } yield PurposeVersionResource(purposeId = purposeUuid, versionId = versionUuid)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error archiving purpose $purposeId with version $versionId", headers) orElse {
        case Success(compactPurpose) =>
          archivePurposeVersion200(headers)(compactPurpose)
      }
    }
  }

  override def updateWaitingForApprovalPurposeVersion(
    purposeId: String,
    versionId: String,
    seed: WaitingForApprovalPurposeVersionUpdateContentSeed
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurposeVersionResource: ToEntityMarshaller[PurposeVersionResource],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Updating purpose $purposeId with version $versionId in waiting for approval state")

    val result: Future[PurposeVersionResource] = for {
      purposeUuid    <- purposeId.toFutureUUID
      versionUuid    <- versionId.toFutureUUID
      purposeVersion <- purposeProcessService.updateWaitingForApprovalPurposeVersion(
        purposeUuid,
        versionUuid,
        seed.toSeed
      )
    } yield PurposeVersionResource(purposeId = purposeUuid, versionId = purposeVersion.id)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error updating purpose $purposeId with version $versionId in waiting for approval state",
        headers
      ) orElse { case Success(resource) =>
        updateWaitingForApprovalPurposeVersion200(headers)(resource)
      }
    }
  }

  override def getRiskAnalysisDocument(purposeId: String, versionId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerFile: ToEntityMarshaller[File]
  ): Route = {
    logger.info(s"Downloading risk analysis document $documentId from purpose $purposeId with version $versionId")

    def parseMediaType(
      contentType: String,
      purposeId: String,
      versionId: String,
      documentId: String
    ): Future[ContentType] =
      ContentType
        .parse(contentType)
        .leftMap(errors => InvalidRiskAnalysisContentType(contentType, purposeId, versionId, documentId, errors))
        .toFuture

    val result: Future[HttpEntity.Strict] =
      for {
        purposeUUID  <- purposeId.toFutureUUID
        documentUUID <- documentId.toFutureUUID
        versionUUID  <- versionId.toFutureUUID
        document     <- purposeProcessService.getRiskAnalysisDocument(purposeUUID, versionUUID, documentUUID)
        contentType  <- parseMediaType(document.contentType, purposeId, versionId, documentId)
        byteStream   <- fileManager.get(ApplicationConfiguration.riskAnalysisDocumentsContainer)(document.path)
      } yield HttpEntity(contentType, byteStream.toByteArray())

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error downloading risk analysis document $documentId from purpose $purposeId with version $versionId",
        headers
      ) orElse { case Success(document) =>
        complete(StatusCodes.OK, headers, document)
      }
    }
  }

  override def createPurposeVersion(purposeId: String, purposeVersionSeed: PurposeVersionSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[PurposeVersionResource],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Creating version for purpose $purposeId with dailyCalls ${purposeVersionSeed.dailyCalls}")

    val result: Future[PurposeVersionResource] = for {
      purposeUuid    <- purposeId.toFutureUUID
      purposeVersion <- purposeProcessService.createPurposeVersion(purposeUuid, purposeVersionSeed.toProcess)
    } yield PurposeVersionResource(purposeId = purposeVersion.id, versionId = purposeVersion.id)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error creating version for purpose $purposeId with dailyCalls ${purposeVersionSeed.dailyCalls}",
        headers
      ) orElse { case Success(resource) =>
        createPurposeVersion200(headers)(resource)
      }
    }
  }

  def deletePurposeVersion(purposeId: String, versionId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Deleting version $versionId of purpose $purposeId")

    val result: Future[Unit] =
      for {
        purposeUUID <- purposeId.toFutureUUID
        versionUUID <- versionId.toFutureUUID
        result      <- purposeProcessService.deletePurposeVersion(purposeUUID, versionUUID)
      } yield result

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error deleting version $versionId of purpose $purposeId", headers) orElse { case Success(_) =>
        deletePurposeVersion204(headers)
      }
    }
  }

  def deletePurpose(
    purposeId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    logger.info(s"Deleting purpose $purposeId")

    val result: Future[Unit] =
      for {
        purposeUUID <- purposeId.toFutureUUID
        _           <- purposeProcessService.deletePurpose(purposeUUID)
      } yield ()

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error deleting purpose $purposeId", headers) orElse { case Success(_) =>
        deletePurpose204(headers)
      }
    }
  }

  def getCurrentVersion(purpose: PurposeProcess.Purpose): Option[PurposeProcess.PurposeVersion] =
    purpose.versions
      .filter(v => v.state != PurposeProcess.PurposeVersionState.WAITING_FOR_APPROVAL)
      .sortBy(_.createdAt)
      .lastOption

  def getWaitingForApproval(purpose: PurposeProcess.Purpose): Option[PurposeProcess.PurposeVersion] =
    purpose.versions.find(_.state == PurposeProcess.PurposeVersionState.WAITING_FOR_APPROVAL)

  private def enhancePurpose(
    purpose: PurposeProcess.Purpose,
    eServices: Seq[CatalogProcess.EService],
    producers: Seq[TenantProcess.Tenant],
    consumers: Seq[TenantProcess.Tenant]
  )(implicit context: Seq[(String, String)]): Future[Purpose] = for {
    requesterId       <- getOrganizationIdFutureUUID(context)
    eService          <- eServices.find(_.id == purpose.eserviceId).toFuture(EServiceNotFound(purpose.eserviceId))
    producer          <- producers.find(_.id == eService.producerId).toFuture(TenantNotFound(eService.producerId))
    consumer          <- consumers.find(_.id == purpose.consumerId).toFuture(TenantNotFound(purpose.consumerId))
    agreement         <- agreementProcessService
      .getLatestAgreement(purpose.consumerId, eService)
      .flatMap(_.toFuture(AgreementNotFound(purpose.consumerId)))
    currentDescriptor <- eService.descriptors
      .find(_.id == agreement.descriptorId)
      .toFuture(EServiceDescriptorNotFound(eService.id.toString, agreement.descriptorId.toString))
    clients           <-
      if (requesterId == purpose.consumerId) getAllClients(purpose.consumerId, Some(purpose.id))
      else Future.successful(Nil)
    currentVersion            = getCurrentVersion(purpose)
    waitingForApprovalVersion = getWaitingForApproval(purpose)
  } yield purpose.toApi(
    eService,
    agreement,
    currentDescriptor,
    currentVersion,
    producer,
    consumer,
    clients,
    waitingForApprovalVersion
  )

  private def getAllClients(consumerId: UUID, purposeId: Option[UUID])(implicit
    contexts: Seq[(String, String)],
    ec: ExecutionContext
  ): Future[List[ClientWithKeys]] = {

    def getClientsFrom(offset: Int): Future[List[ClientWithKeys]] =
      authorizationProcessService
        .getClientsWithKeys(
          name = None,
          relationshipIds = Seq.empty,
          consumerId = consumerId,
          purposeId = purposeId,
          kind = None,
          limit = 50,
          offset = offset
        )
        .map(_.results.toList)

    def go(start: Int)(as: List[ClientWithKeys]): Future[List[ClientWithKeys]] =
      getClientsFrom(start).flatMap(clients =>
        if (clients.size < 50) Future.successful(as ++ clients) else go(start + 50)(as ++ clients)
      )

    go(0)(Nil)
  }

  override def clonePurpose(purposeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurposeVersionResource: ToEntityMarshaller[PurposeVersionResource],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Cloning purpose $purposeId")

    val result: Future[PurposeVersionResource] = for {
      purposeUuid  <- purposeId.toFutureUUID
      purpose      <- purposeProcessService.clonePurpose(purposeUuid)
      draftVersion <- purpose.versions
        .find(_.state == PurposeProcess.PurposeVersionState.DRAFT)
        .toFuture(PurposeVersionDraftNotFound(purpose.id))
    } yield PurposeVersionResource(purpose.id, draftVersion.id)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error cloning purpose $purposeId", headers) orElse { case Success(resource) =>
        clonePurpose200(headers)(resource)
      }
    }
  }

  override def suspendPurposeVersion(purposeId: String, versionId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersionResource],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Suspending Version $versionId of Purpose $purposeId")

    val result: Future[PurposeVersionResource] = for {
      purposeUUID <- purposeId.toFutureUUID
      versionUUID <- versionId.toFutureUUID
      _           <- purposeProcessService.suspendPurposeVersion(purposeId = purposeUUID, versionId = versionUUID)
    } yield PurposeVersionResource(purposeUUID, versionUUID)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error suspending Version $versionId of Purpose $purposeId", headers) orElse { case Success(r) =>
        suspendPurposeVersion200(headers)(r)
      }
    }
  }

  override def activatePurposeVersion(purposeId: String, versionId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurposeVersionResource: ToEntityMarshaller[PurposeVersionResource],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Activating Version $versionId of Purpose $purposeId")

    val result: Future[PurposeVersionResource] = for {
      purposeUUID <- purposeId.toFutureUUID
      versionUUID <- versionId.toFutureUUID
      _           <- purposeProcessService.activatePurposeVersion(purposeId = purposeUUID, versionId = versionUUID)
    } yield PurposeVersionResource(purposeUUID, versionUUID)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error activating Version $versionId of Purpose $purposeId", headers) orElse { case Success(r) =>
        activatePurposeVersion200(headers)(r)
      }
    }
  }

  override def createPurpose(purposeSeed: PurposeSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {
    logger.info(s"Creating purpose with eService ${purposeSeed.eserviceId} and consumer ${purposeSeed.consumerId}")

    val result: Future[CreatedResource] =
      purposeProcessService.createPurpose(purposeSeed.toProcess)(contexts).map(_.toApiResource)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error creating Purpose with eService ${purposeSeed.eserviceId} and consumer ${purposeSeed.consumerId}",
        headers
      ) orElse { case Success(purpose) =>
        createPurpose200(headers)(purpose)
      }
    }
  }

  override def getPurpose(purposeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Retrieving Purpose $purposeId")

    val result: Future[Purpose] = for {
      purposeUUID     <- purposeId.toFutureUUID
      purpose         <- purposeProcessService.getPurpose(purposeUUID)
      eService        <- catalogProcessService.getEServiceById(purpose.eserviceId)
      agreement       <- agreementProcessService
        .getLatestAgreement(purpose.consumerId, eService)
        .flatMap(_.toFuture(AgreementNotFound(purpose.consumerId)))
      consumer        <- tenantProcessService.getTenant(agreement.consumerId)
      producer        <- tenantProcessService.getTenant(agreement.producerId)
      enhancedPurpose <- enhancePurpose(purpose, Seq(eService), Seq(producer), Seq(consumer))
    } yield enhancedPurpose

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving purpose $purposeId", headers) orElse { case Success(response) =>
        getPurpose200(headers)(response)
      }
    }
  }

  override def updatePurpose(purposeId: String, purposeUpdateContent: PurposeUpdateContent)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurposeVersionResource: ToEntityMarshaller[PurposeVersionResource],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Updating Purpose $purposeId")

    val result: Future[PurposeVersionResource] = for {
      purposeUUID    <- purposeId.toFutureUUID
      updatedPurpose <- purposeProcessService
        .updatePurpose(purposeUUID, purposeUpdateContent.toProcess)
      versionUUID    <- getCurrentVersion(updatedPurpose).map(_.id).toFuture(PurposeNotFound(purposeUUID))
    } yield PurposeVersionResource(purposeUUID, versionUUID)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error updating Purpose $purposeId", headers) orElse { case Success(response) =>
        updatePurpose200(headers)(response)
      }
    }
  }

  override def updateReversePurpose(purposeId: String, payload: ReversePurposeUpdateContent)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurposeVersionResource: ToEntityMarshaller[PurposeVersionResource],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Updating Reverse Purpose $purposeId")

    val result: Future[PurposeVersionResource] = for {
      purposeUUID    <- purposeId.toFutureUUID
      updatedPurpose <- purposeProcessService.updateReversePurpose(purposeUUID, payload.toProcess)
      versionUUID    <- getCurrentVersion(updatedPurpose).map(_.id).toFuture(PurposeNotFound(purposeUUID))
    } yield PurposeVersionResource(purposeUUID, versionUUID)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error updating reverse Purpose $purposeId", headers) orElse { case Success(response) =>
        updateReversePurpose200(headers)(response)
      }
    }
  }

  override def retrieveLatestRiskAnalysisConfiguration()(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerRiskAnalysisFormConfig: ToEntityMarshaller[RiskAnalysisFormConfig]
  ): Route = {
    logger.info(s"Retrieving risk analysis latest configuration")

    val result: Future[RiskAnalysisFormConfig] = purposeProcessService
      .retrieveLatestRiskAnalysisConfiguration()
      .map(_.toApi)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving latest risk analysis configuration", headers) orElse { case Success(response) =>
        retrieveLatestRiskAnalysisConfiguration200(headers)(response)
      }
    }
  }

  override def retrieveRiskAnalysisConfigurationByVersion(riskAnalysisVersion: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerRiskAnalysisFormConfig: ToEntityMarshaller[RiskAnalysisFormConfig]
  ): Route = {
    logger.info(s"Retrieving risk analysis latest configuration for version $riskAnalysisVersion")

    val result: Future[RiskAnalysisFormConfig] = purposeProcessService
      .retrieveRiskAnalysisConfigurationByVersion(riskAnalysisVersion)
      .map(_.toApi)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving risk analysis configuration for version $riskAnalysisVersion", headers) orElse {
        case Success(response) =>
          retrieveRiskAnalysisConfigurationByVersion200(headers)(response)
      }
    }
  }

  override def createPurposeForReceiveEservice(seed: PurposeEServiceSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {
    logger.info(s"Creating purpose from ESErvice ${seed.eserviceId} and Risk Analysis ${seed.riskAnalysisId}")

    val result: Future[CreatedResource] =
      purposeProcessService.createPurposeFromEService(seed.toProcess)(contexts).map(_.toApiResource)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error creating Purpose with eService ${seed.eserviceId} and consumer ${seed.consumerId}",
        headers
      ) orElse { case Success(purpose) =>
        createPurposeForReceiveEservice200(headers)(purpose)
      }
    }
  }
}
