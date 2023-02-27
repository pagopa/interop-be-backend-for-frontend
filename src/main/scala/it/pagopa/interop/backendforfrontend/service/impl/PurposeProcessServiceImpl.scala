package it.pagopa.interop.backendforfrontend.service.impl

import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.PurposeProcessService
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders
import it.pagopa.interop.purposeprocess.client.api.{EnumsSerializers, PurposeApi}
import it.pagopa.interop.purposeprocess.client.invoker.{ApiInvoker, ApiRequest, BearerToken}
import it.pagopa.interop.purposeprocess.client.model.{PurposeVersion, PurposeVersionState, Purposes}

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
}
