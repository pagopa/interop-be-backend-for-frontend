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
    excludeDraft: Option[Boolean],
    offset: Int,
    limit: Int
  )(implicit contexts: Seq[(String, String)]): Future[Purposes] =
    withHeaders[Purposes] { (bearerToken, correlationId) =>
      val request: ApiRequest[Purposes] =
        api.getPurposes(
          name = name,
          eservicesIds = eServicesIds,
          consumersIds = consumersIds,
          producersIds = producersIds,
          states = states,
          excludeDraft = excludeDraft,
          offset = offset,
          limit = limit,
          xCorrelationId = correlationId
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Retrieving Purposes")
    }

  override def archivePurposeVersion(purposeId: UUID, versionId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[PurposeVersion] =
    withHeaders[PurposeVersion] { (bearerToken, correlationId) =>
      val request: ApiRequest[PurposeVersion] =
        api.archivePurposeVersion(purposeId = purposeId, versionId = versionId, xCorrelationId = correlationId)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Archive Purpose $purposeId with version $versionId")
    }

  override def clonePurpose(purposeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Purpose] =
    withHeaders[Purpose] { (bearerToken, correlationId) =>
      val request: ApiRequest[Purpose] =
        api.clonePurpose(purposeId = purposeId, xCorrelationId = correlationId)(BearerToken(bearerToken))
      invoker.invoke(request, s"Cloning Purpose ${purposeId.toString}")
    }

  override def createPurposeVersion(purposeId: UUID, seed: PurposeVersionSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[PurposeVersion] =
    withHeaders[PurposeVersion] { (bearerToken, correlationId) =>
      val request: ApiRequest[PurposeVersion] =
        api.createPurposeVersion(purposeId = purposeId, purposeVersionSeed = seed, xCorrelationId = correlationId)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Creating version for purpose $purposeId")
    }

  override def deletePurposeVersion(purposeId: UUID, versionId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] =
    withHeaders[Unit] { (bearerToken, correlationId) =>
      val request: ApiRequest[Unit] =
        api.deletePurposeVersion(purposeId = purposeId, versionId = versionId, xCorrelationId = correlationId)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Deleting version $versionId of Purpose $purposeId")
    }

  override def suspendPurposeVersion(purposeId: UUID, versionId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[PurposeVersion] = withHeaders { (bearerToken, correlationId) =>
    val request: ApiRequest[PurposeVersion] =
      api.suspendPurposeVersion(xCorrelationId = correlationId, purposeId = purposeId, versionId = versionId)(
        BearerToken(bearerToken)
      )
    invoker.invoke(request, s"Suspending Version ${versionId.toString} of Purpose ${purposeId.toString}")
  }

  override def updateWaitingForApprovalPurposeVersion(
    purposeId: UUID,
    versionId: UUID,
    updateContent: WaitingForApprovalPurposeVersionUpdateContent
  )(implicit contexts: Seq[(String, String)]): Future[PurposeVersion] = withHeaders[PurposeVersion] {
    (bearerToken, correlationId) =>
      val request: ApiRequest[PurposeVersion] =
        api.updateWaitingForApprovalPurposeVersion(
          purposeId = purposeId,
          versionId = versionId,
          waitingForApprovalPurposeVersionUpdateContent = updateContent,
          xCorrelationId = correlationId
        )(BearerToken(bearerToken))
      invoker.invoke(
        request,
        s"Updating purpose ${purposeId.toString} version ${versionId.toString} with waiting for approval state"
      )
  }

  override def deletePurpose(purposeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit] =
    withHeaders[Unit] { (bearerToken, correlationId) =>
      val request: ApiRequest[Unit] =
        api.deletePurpose(id = purposeId, xCorrelationId = correlationId)(BearerToken(bearerToken))
      invoker.invoke(request, s"Deleting Purposes ${purposeId.toString}")
    }

  override def getRiskAnalysisDocument(purposeId: UUID, versionId: UUID, documentId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[PurposeVersionDocument] = withHeaders[PurposeVersionDocument] { (bearerToken, correlationId) =>
    val request: ApiRequest[PurposeVersionDocument] =
      api.getRiskAnalysisDocument(
        purposeId = purposeId.toString,
        versionId = versionId.toString,
        documentId = documentId.toString,
        xCorrelationId = correlationId
      )(BearerToken(bearerToken))
    invoker.invoke(
      request,
      s"Downloading Risk Analysis document ${documentId.toString} for Purpose ${purposeId.toString} and Version ${versionId.toString}"
    )
  }

  override def activatePurposeVersion(purposeId: UUID, versionId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[PurposeVersion] = withHeaders { (bearerToken, correlationId) =>
    val request: ApiRequest[PurposeVersion] =
      api.activatePurposeVersion(xCorrelationId = correlationId, purposeId = purposeId, versionId = versionId)(
        BearerToken(bearerToken)
      )
    invoker.invoke(request, s"Activating Version ${versionId.toString} of Purpose ${purposeId.toString}")
  }

  override def createPurpose(seed: PurposeSeed)(implicit contexts: Seq[(String, String)]): Future[Purpose] =
    withHeaders { (bearerToken, correlationId) =>
      val request: ApiRequest[Purpose] =
        api.createPurpose(xCorrelationId = correlationId, purposeSeed = seed)(BearerToken(bearerToken))
      invoker.invoke(request, s"Creating Purpose")
    }

  override def getPurpose(id: UUID)(implicit contexts: Seq[(String, String)]): Future[Purpose] = withHeaders[Purpose] {
    (bearerToken, correlationId) =>
      val request: ApiRequest[Purpose] =
        api.getPurpose(xCorrelationId = correlationId, id = id)(BearerToken(bearerToken))
      invoker.invoke(request, s"Retriving purpose with purposeId $id")
  }

  override def createPurposeFromEService(
    eServicePurposeSeed: EServicePurposeSeed
  )(implicit contexts: Seq[(String, String)]): Future[Purpose] = withHeaders[Purpose] { (bearerToken, correlationId) =>
    val request: ApiRequest[Purpose] =
      api.createPurposeFromEService(xCorrelationId = correlationId, eServicePurposeSeed = eServicePurposeSeed)(
        BearerToken(bearerToken)
      )
    invoker.invoke(
      request,
      s"Creating purpose from ESErvice ${eServicePurposeSeed.eServiceId} and Risk Analysis ${eServicePurposeSeed.riskAnalysisId}"
    )
  }

  override def updatePurpose(id: UUID, purposeUpdateContent: PurposeUpdateContent)(implicit
    contexts: Seq[(String, String)]
  ): Future[Purpose] = withHeaders[Purpose] { (bearerToken, correlationId) =>
    val request: ApiRequest[Purpose] =
      api.updatePurpose(xCorrelationId = correlationId, id = id, purposeUpdateContent = purposeUpdateContent)(
        BearerToken(bearerToken)
      )
    invoker.invoke(request, s"Updating purpose $id")
  }

  override def updateReversePurpose(id: UUID, reversePurposeUpdateContent: ReversePurposeUpdateContent)(implicit
    contexts: Seq[(String, String)]
  ): Future[Purpose] = withHeaders[Purpose] { (bearerToken, correlationId) =>
    val request: ApiRequest[Purpose] =
      api.updateReversePurpose(
        xCorrelationId = correlationId,
        id = id,
        reversePurposeUpdateContent = reversePurposeUpdateContent
      )(BearerToken(bearerToken))
    invoker.invoke(request, s"Updating reverse purpose $id")
  }

  override def retrieveLatestRiskAnalysisConfiguration()(implicit
    contexts: Seq[(String, String)]
  ): Future[RiskAnalysisFormConfigResponse] = withHeaders[RiskAnalysisFormConfigResponse] {
    (bearerToken, correlationId) =>
      val request: ApiRequest[RiskAnalysisFormConfigResponse] =
        api.retrieveLatestRiskAnalysisConfiguration(xCorrelationId = correlationId)(BearerToken(bearerToken))
      invoker.invoke(request, s"Retrieving latest risk analysis configuration")
  }

  override def retrieveRiskAnalysisConfigurationByVersion(riskAnalysisVersion: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[RiskAnalysisFormConfigResponse] = withHeaders[RiskAnalysisFormConfigResponse] {
    (bearerToken, correlationId) =>
      val request: ApiRequest[RiskAnalysisFormConfigResponse] =
        api.retrieveRiskAnalysisConfigurationByVersion(
          riskAnalysisVersion = riskAnalysisVersion,
          xCorrelationId = correlationId
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Retrieving risk analysis configuration for version $riskAnalysisVersion")
  }
}
