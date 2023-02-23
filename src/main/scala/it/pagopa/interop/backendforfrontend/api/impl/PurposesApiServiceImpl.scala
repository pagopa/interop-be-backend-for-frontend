package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Route
import cats.syntax.all._
import akka.http.scaladsl.model.{ContentType, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.PurposesApiService
import it.pagopa.interop.backendforfrontend.error.BFFErrors.{
  EServiceNotFound,
  TenantNotFound,
  InvalidRiskAnalysisContentType
}
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.purposeprocess.client.{model => PurposeProcess}
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}
import it.pagopa.interop.tenantprocess.client.{model => TenantProcess}
import it.pagopa.interop.backendforfrontend.service.{CatalogProcessService, PurposeProcessService, TenantProcessService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.backendforfrontend.service.types.PurposeProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.commons.files.service.FileManager

import scala.concurrent.{ExecutionContext, Future}
import java.io.File
import scala.util.Success

final case class PurposesApiServiceImpl(
  catalogProcessService: CatalogProcessService,
  purposeProcessService: PurposeProcessService,
  tenantProcessService: TenantProcessService,
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
        enhancedResults <- pagedResults.results.traverse(enhancePurpose(_, eServices, producers, consumers))
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

  private def enhancePurpose(
    purpose: PurposeProcess.Purpose,
    eServices: Seq[CatalogProcess.EService],
    producers: Seq[TenantProcess.Tenant],
    consumers: Seq[TenantProcess.Tenant]
  ): Future[Purpose] = for {
    eService <- eServices.find(_.id == purpose.eserviceId).toFuture(EServiceNotFound(purpose.eserviceId))
    producer <- producers.find(_.id == eService.producerId).toFuture(TenantNotFound(eService.producerId))
    consumer <- consumers.find(_.id == purpose.consumerId).toFuture(TenantNotFound(purpose.consumerId))
    currentVersion            = purpose.versions
      .filter(v => v.state != PurposeProcess.PurposeVersionState.WAITING_FOR_APPROVAL)
      .sortBy(_.createdAt)
      .lastOption
    waitingForApprovalVersion = purpose.versions.find(
      _.state == PurposeProcess.PurposeVersionState.WAITING_FOR_APPROVAL
    )
  } yield Purpose(
    id = purpose.id,
    title = purpose.title,
    consumer = CompactOrganization(id = consumer.id, name = consumer.name),
    eservice = CompactEService(
      id = eService.id,
      name = eService.name,
      producer = CompactOrganization(id = producer.id, name = producer.name)
    ),
    currentVersion =
      currentVersion.map(v => CompactPurposeVersion(id = v.id, state = v.state.toApi, dailyCalls = v.dailyCalls)),
    waitingForApprovalVersion = waitingForApprovalVersion.map(v =>
      CompactPurposeVersion(id = v.id, state = v.state.toApi, dailyCalls = v.dailyCalls, v.expectedApprovalDate)
    ),
    suspendedByConsumer = purpose.suspendedByConsumer,
    suspendedByProducer = purpose.suspendedByProducer
  )
}
