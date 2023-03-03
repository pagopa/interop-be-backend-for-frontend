package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{ContentType, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.PurposesApiService
import it.pagopa.interop.backendforfrontend.api.impl.Utils.{getLatestAgreement, isUpgradable}
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.BFFErrors._
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service.types.PurposeProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.{
  AgreementProcessService,
  AuthorizationManagementService,
  CatalogProcessService,
  PurposeProcessService,
  TenantProcessService
}
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getOrganizationIdFutureUUID
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.purposeprocess.client.{model => PurposeProcess}
import it.pagopa.interop.tenantprocess.client.{model => TenantProcess}

import java.io.File
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class PurposesApiServiceImpl(
  catalogProcessService: CatalogProcessService,
  purposeProcessService: PurposeProcessService,
  tenantProcessService: TenantProcessService,
  agreementProcessService: AgreementProcessService,
  authorizationManagementService: AuthorizationManagementService,
  fileManager: FileManager
)(implicit ec: ExecutionContext)
    extends PurposesApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getPurposes(
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
    val result: Future[Purposes] =
      for {
        requesterId    <- getOrganizationIdFutureUUID(contexts)
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
          offset = offset,
          limit = limit
        )
        actualEServicesIds = pagedResults.results.map(_.eserviceId).distinct
        actualConsumersIds = pagedResults.results.map(_.consumerId).distinct
        eServices       <- actualEServicesIds.traverse(catalogProcessService.getEServiceById)
        producers       <- eServices.map(_.producerId).distinct.traverse(tenantProcessService.getTenant)
        consumers       <- actualConsumersIds.traverse(tenantProcessService.getTenant)
        enhancedResults <- pagedResults.results.traverse(
          enhancePurpose(_, eServices, producers, consumers, requesterId)
        )
      } yield Purposes(
        results = enhancedResults,
        pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
      )

    onComplete(result) {
      handleError(
        s"Error retrieving Purposes for name $q, EServices $eServicesIds, Consumers $consumersIds offset $offset, limit $limit"
      ) orElse { case Success(r) => getPurposes200(r) }
    }
  }

  override def archivePurposeVersion(purposeId: String, versionId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurposeVersionResource: ToEntityMarshaller[PurposeVersionResource],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[PurposeVersionResource] = for {
      purposeUuid <- purposeId.toFutureUUID
      versionUuid <- versionId.toFutureUUID
      _           <- purposeProcessService.archivePurposeVersion(purposeUuid, versionUuid)
    } yield PurposeVersionResource(purposeId = purposeUuid, versionId = versionUuid)

    onComplete(result) {
      handleError(s"Error archiving purpose $purposeId with version $versionId") orElse {
        case Success(compactPurpose) =>
          archivePurposeVersion200(compactPurpose)
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
      handleError(s"Error updating purpose $purposeId with version $versionId in waiting for approval state") orElse {
        case Success(resource) =>
          updateWaitingForApprovalPurposeVersion200(resource)
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
      handleError(
        s"Error downloading risk analysis document $documentId from purpose $purposeId with version $versionId"
      ) orElse { case Success(document) =>
        complete(document)
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
      handleError(
        s"Error creating version for purpose $purposeId with dailyCalls ${purposeVersionSeed.dailyCalls}"
      ) orElse { case Success(resource) =>
        createPurposeVersion200(resource)
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
      handleError(s"Error deleting version $versionId of purpose $purposeId") orElse { case Success(_) =>
        deletePurposeVersion204
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
      handleError(s"Error deleting purpose $purposeId") orElse { case Success(_) =>
        deletePurpose204
      }
    }
  }

  private def enhancePurpose(
    purpose: PurposeProcess.Purpose,
    eServices: Seq[CatalogProcess.EService],
    producers: Seq[TenantProcess.Tenant],
    consumers: Seq[TenantProcess.Tenant],
    requesterId: UUID
  )(implicit context: Seq[(String, String)]): Future[Purpose] = for {
    eService  <- eServices.find(_.id == purpose.eserviceId).toFuture(EServiceNotFound(purpose.eserviceId))
    agreement <- getLatestAgreement(requesterId, eService, ec, agreementProcessService)(context)
    clients   <- authorizationManagementService.getClients(Some(purpose.id))(context)
    producer  <- producers.find(_.id == eService.producerId).toFuture(TenantNotFound(eService.producerId))
    consumer  <- consumers.find(_.id == purpose.consumerId).toFuture(TenantNotFound(purpose.consumerId))
    currentVersion            = purpose.versions
      .filter(v => v.state != PurposeProcess.PurposeVersionState.WAITING_FOR_APPROVAL)
      .sortBy(_.createdAt)
      .lastOption
    waitingForApprovalVersion = purpose.versions.find(
      _.state == PurposeProcess.PurposeVersionState.WAITING_FOR_APPROVAL
    )
  } yield purpose.toApi(
    purpose,
    eService,
    agreement,
    currentVersion,
    producer,
    consumer,
    clients,
    waitingForApprovalVersion
  )

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
      handleError(s"Error cloning purpose $purposeId") orElse { case Success(resource) =>
        clonePurpose200(resource)
      }
    }
  }

  override def suspendPurposeVersion(purposeId: String, versionId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersionResource],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[PurposeVersionResource] = for {
      purposeUUID <- purposeId.toFutureUUID
      versionUUID <- versionId.toFutureUUID
      _           <- purposeProcessService.suspendPurposeVersion(purposeId = purposeUUID, versionId = versionUUID)
    } yield PurposeVersionResource(purposeUUID, versionUUID)

    onComplete(result) {
      handleError(s"Error suspending Version $versionId of Purpose $purposeId") orElse { case Success(r) =>
        suspendPurposeVersion200(r)
      }
    }
  }

  override def activatePurposeVersion(purposeId: String, versionId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurposeVersionResource: ToEntityMarshaller[PurposeVersionResource],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[PurposeVersionResource] = for {
      purposeUUID <- purposeId.toFutureUUID
      versionUUID <- versionId.toFutureUUID
      _           <- purposeProcessService.activatePurposeVersion(purposeId = purposeUUID, versionId = versionUUID)
    } yield PurposeVersionResource(purposeUUID, versionUUID)

    onComplete(result) {
      handleError(s"Error activating Version $versionId of Purpose $purposeId") orElse { case Success(r) =>
        activatePurposeVersion200(r)
      }
    }
  }

  override def updateDraftPurposeVersion(
    purposeId: String,
    versionId: String,
    draftPurposeVersionUpdateContent: DraftPurposeVersionUpdateContent
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurposeVersion: ToEntityMarshaller[PurposeVersionResource],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[PurposeVersionResource] = for {
      purposeUUID <- purposeId.toFutureUUID
      versionUUID <- versionId.toFutureUUID
      _           <- purposeProcessService
        .updateDraftPurposeVersion(purposeUUID, versionUUID, draftPurposeVersionUpdateContent.toProcess)
    } yield PurposeVersionResource(purposeUUID, versionUUID)

    onComplete(result) {
      handleError(s"Error updating draft version $versionId of purpose $purposeId") orElse { case Success(response) =>
        updateDraftPurposeVersion200(response)
      }
    }
  }

  override def getPurpose(purposeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Purpose] = for {
      purposeUUID     <- purposeId.toFutureUUID
      requesterId     <- getOrganizationIdFutureUUID(contexts)
      purpose         <- purposeProcessService.getPurpose(purposeUUID)
      enhancedPurpose <- enhancePurpose(purpose, eServices, producers, consumers, requesterId)
    } yield enhancedPurpose

    onComplete(result) {
      handleError(s"Error updating draft version of purpose $purposeId") orElse { case Success(response) =>
        getPurpose200(response)
      }
    }
  }
}
