package it.pagopa.interop.backendforfrontend.service.impl

import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.PurposeProcessService
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders
import it.pagopa.interop.purposeprocess.client.api.{EnumsSerializers, PurposeApi}
import it.pagopa.interop.purposeprocess.client.invoker.{ApiInvoker, ApiRequest, BearerToken}
import it.pagopa.interop.purposeprocess.client.model._

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}

class PurposeProcessServiceImpl(purposeProcessUrl: String, blockingEc: ExecutionContextExecutor)(implicit
  system: ActorSystem[_]
) extends PurposeProcessService {

  val invoker: ApiInvoker = ApiInvoker(EnumsSerializers.all, blockingEc)(system.classicSystem)
  val api: PurposeApi     = PurposeApi(purposeProcessUrl)

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getPurposes(
    name: Option[String],
    eServicesIds: Seq[UUID],
    consumersIds: Seq[UUID],
    producersIds: Seq[UUID],
    states: Seq[PurposeVersionState],
    offset: Int,
    limit: Int
  )(implicit contexts: Seq[(String, String)]): Future[Purposes] =
    withHeaders[Purposes] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Purposes] =
        api.getPurposes(
          name = name,
          eservicesIds = eServicesIds,
          consumersIds = consumersIds,
          producersIds = producersIds,
          states = states,
          offset = offset,
          limit = limit,
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Retrieving Purposes")
    }

  override def archivePurposeVersion(purposeId: UUID, versionId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[PurposeVersion] =
    withHeaders[PurposeVersion] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[PurposeVersion] =
        api.archivePurposeVersion(
          purposeId = purposeId,
          versionId = versionId,
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Archive Purpose $purposeId with version $versionId")
    }

  override def clonePurpose(purposeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Purpose] =
    withHeaders[Purpose] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Purpose] =
        api.clonePurpose(purposeId = purposeId, xCorrelationId = correlationId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Cloning Purpose ${purposeId.toString}")
    }

  override def createPurposeVersion(purposeId: UUID, seed: PurposeVersionSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[PurposeVersion] =
    withHeaders[PurposeVersion] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[PurposeVersion] =
        api.createPurposeVersion(
          purposeId = purposeId,
          purposeVersionSeed = seed,
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Creating version for purpose $purposeId")
    }

  override def deletePurposeVersion(purposeId: UUID, versionId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] =
    withHeaders[Unit] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Unit] =
        api.deletePurposeVersion(
          purposeId = purposeId,
          versionId = versionId,
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Deleting version $versionId of Purpose $purposeId")
    }

  override def suspendPurposeVersion(purposeId: UUID, versionId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[PurposeVersion] = withHeaders { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[PurposeVersion] =
      api.suspendPurposeVersion(
        xCorrelationId = correlationId,
        purposeId = purposeId,
        versionId = versionId,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(request, s"Suspending Version $versionId of Purpose $purposeId")
  }

  override def updateWaitingForApprovalPurposeVersion(
    purposeId: UUID,
    versionId: UUID,
    updateContent: WaitingForApprovalPurposeVersionUpdateContent
  )(implicit contexts: Seq[(String, String)]): Future[PurposeVersion] = withHeaders[PurposeVersion] {
    (bearerToken, correlationId, ip) =>
      val request: ApiRequest[PurposeVersion] =
        api.updateWaitingForApprovalPurposeVersion(
          purposeId = purposeId,
          versionId = versionId,
          waitingForApprovalPurposeVersionUpdateContent = updateContent,
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(
        request,
        s"Updating purpose ${purposeId.toString} version ${versionId.toString} with waiting for approval state"
      )
  }

  override def deletePurpose(purposeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit] =
    withHeaders[Unit] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Unit] =
        api.deletePurpose(id = purposeId, xCorrelationId = correlationId, xForwardedFor = ip)(BearerToken(bearerToken))
      invoker.invoke(request, s"Deleting Purposes $purposeId")
    }

  override def getRiskAnalysisDocument(purposeId: UUID, versionId: UUID, documentId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[PurposeVersionDocument] = withHeaders[PurposeVersionDocument] { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[PurposeVersionDocument] =
      api.getRiskAnalysisDocument(
        purposeId = purposeId.toString,
        versionId = versionId.toString,
        documentId = documentId.toString,
        xCorrelationId = correlationId,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(
      request,
      s"Downloading Risk Analysis document $documentId for Purpose $purposeId and Version $versionId"
    )
  }

  override def activatePurposeVersion(purposeId: UUID, versionId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[PurposeVersion] = withHeaders { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[PurposeVersion] =
      api.activatePurposeVersion(
        xCorrelationId = correlationId,
        purposeId = purposeId,
        versionId = versionId,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(request, s"Activating Version $versionId of Purpose $purposeId")
  }
  override def updateDraftPurposeVersion(
    purposeId: UUID,
    versionId: UUID,
    updateContent: DraftPurposeVersionUpdateContent
  )(implicit contexts: Seq[(String, String)]): Future[PurposeVersion] = withHeaders[PurposeVersion] {
    (bearerToken, correlationId, ip) =>
      val request: ApiRequest[PurposeVersion] =
        api.updateDraftPurposeVersion(
          xCorrelationId = correlationId,
          purposeId = purposeId,
          versionId = versionId,
          draftPurposeVersionUpdateContent = updateContent,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Updating draft version $versionId of purpose $purposeId")
  }

  override def createPurpose(seed: PurposeSeed)(implicit contexts: Seq[(String, String)]): Future[Purpose] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Purpose] =
        api.createPurpose(xCorrelationId = correlationId, purposeSeed = seed, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Creating Purpose")
    }

  override def getPurpose(id: UUID)(implicit contexts: Seq[(String, String)]): Future[Purpose] = withHeaders[Purpose] {
    (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Purpose] =
        api.getPurpose(xCorrelationId = correlationId, id = id, xForwardedFor = ip)(BearerToken(bearerToken))
      invoker.invoke(request, s"Retriving purpose with purposeId $id")
  }
}
