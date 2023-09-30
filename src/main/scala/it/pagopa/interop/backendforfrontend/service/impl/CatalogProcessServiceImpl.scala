package it.pagopa.interop.backendforfrontend.service.impl

import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.CatalogProcessService
import it.pagopa.interop.catalogprocess.client.api.{EnumsSerializers, ProcessApi}
import it.pagopa.interop.catalogprocess.client.invoker.{ApiInvoker, ApiRequest, BearerToken}
import it.pagopa.interop.catalogprocess.client.model._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}

class CatalogProcessServiceImpl(catalogProcessUrl: String, blockingEc: ExecutionContextExecutor)(implicit
  system: ActorSystem[_]
) extends CatalogProcessService {

  val invoker: ApiInvoker = ApiInvoker(EnumsSerializers.all, blockingEc)(system.classicSystem)
  val api: ProcessApi     = ProcessApi(catalogProcessUrl)

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def createEService(eServiceSeed: EServiceSeed)(implicit contexts: Seq[(String, String)]): Future[EService] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[EService] =
        api.createEService(xCorrelationId = correlationId, eServiceSeed = eServiceSeed, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Eservice created")
    }

  def activateDescriptor(eServiceId: UUID, descriptorId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Unit] =
        api.activateDescriptor(
          xCorrelationId = correlationId,
          eServiceId = eServiceId,
          descriptorId = descriptorId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Descriptor activated")
    }

  override def getEServices(
    name: Option[String] = None,
    eServicesIds: Seq[UUID],
    producersIds: Seq[UUID],
    attributesIds: Seq[UUID],
    agreementStates: Seq[AgreementState],
    states: Seq[EServiceDescriptorState],
    offset: Int,
    limit: Int
  )(implicit contexts: Seq[(String, String)]): Future[EServices] =
    withHeaders[EServices] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[EServices] = api.getEServices(
        name = name,
        eservicesIds = eServicesIds,
        producersIds = producersIds,
        attributesIds = attributesIds,
        agreementStates = agreementStates,
        states = states,
        offset = offset,
        limit = limit,
        xCorrelationId = correlationId,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      invoker.invoke(
        request,
        s"Retrieving EServices for name = $name, producersIds = $producersIds, attributesIds = $attributesIds, states = $states, offset = $offset, limit = $limit,"
      )
    }

  override def getEServiceById(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[EService] =
        api.getEServiceById(xCorrelationId = correlationId, eServiceId = eServiceId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Retrieving EService for ${eServiceId.toString} from Catalog Process")
    }

  def createDescriptor(eServiceId: UUID, eServiceDescriptorSeed: EServiceDescriptorSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[EServiceDescriptor] = {
    withHeaders { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[EServiceDescriptor] =
        api.createDescriptor(
          xCorrelationId = correlationId,
          eServiceId = eServiceId,
          eServiceDescriptorSeed = eServiceDescriptorSeed,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Create descriptor for ${eServiceId.toString} ")
    }
  }

  def publishDescriptor(eServiceId: UUID, descriptorId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Unit] =
        api.publishDescriptor(
          xCorrelationId = correlationId,
          eServiceId = eServiceId,
          descriptorId = descriptorId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(
        request,
        s"Publishing Descriptor ${descriptorId.toString} EService for ${eServiceId.toString} from Catalog Process"
      )
    }

  override def suspendDescriptor(eServiceId: UUID, descriptorId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[Unit] =
      api.suspendDescriptor(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(request, s"Suspending EService ${eServiceId.toString} from Catalog Process")
  }

  def updateEServiceDocumentById(
    eServiceId: UUID,
    descriptorId: UUID,
    documentId: UUID,
    updateEServiceDescriptorDocumentSeed: UpdateEServiceDescriptorDocumentSeed
  )(implicit contexts: Seq[(String, String)]): Future[EServiceDoc] = withHeaders { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[EServiceDoc] =
      api.updateEServiceDocumentById(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        documentId = documentId,
        updateEServiceDescriptorDocumentSeed = updateEServiceDescriptorDocumentSeed,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(
      request,
      s"Updating document ${documentId.toString} on eService ${eServiceId.toString} for descriptor ${descriptorId.toString} with seed $updateEServiceDescriptorDocumentSeed from Catalog Process"
    )
  }

  override def cloneEServiceByDescriptor(eServiceId: UUID, descriptorId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[EService] = withHeaders { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[EService] =
      api.cloneEServiceByDescriptor(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(request, s"Cloning EService ${eServiceId.toString} with descriptor ${descriptorId.toString}")
  }

  override def deleteEServiceDocumentById(eServiceId: UUID, descriptorId: UUID, documentId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[Unit] =
      api.deleteEServiceDocumentById(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        documentId = documentId,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(
      request,
      s"Deleting document ${documentId.toString} on eService ${eServiceId.toString} for descriptor ${descriptorId.toString}"
    )
  }

  override def updateDraftDescriptor(
    eServiceId: UUID,
    descriptorId: UUID,
    updateEServiceDescriptorSeed: UpdateEServiceDescriptorSeed
  )(implicit contexts: Seq[(String, String)]): Future[EService] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[EService] =
        api.updateDraftDescriptor(
          xCorrelationId = correlationId,
          eServiceId = eServiceId,
          descriptorId = descriptorId,
          updateEServiceDescriptorSeed = updateEServiceDescriptorSeed,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(
        request,
        s"Update draft descriptor ${descriptorId.toString} for EService ${eServiceId.toString} with seed $updateEServiceDescriptorSeed"
      )
    }

  override def createEServiceDocument(
    eServiceId: UUID,
    descriptorId: UUID,
    documentSeed: CreateEServiceDescriptorDocumentSeed
  )(implicit contexts: Seq[(String, String)]): Future[EService] = withHeaders { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[EService] =
      api.createEServiceDocument(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        createEServiceDescriptorDocumentSeed = documentSeed,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(
      request,
      s"Creating eService document ${documentSeed.documentId.toString} of kind ${documentSeed.kind}, name ${documentSeed.fileName}, path ${documentSeed.filePath} for eService $eServiceId and descriptor $descriptorId"
    )
  }
  override def updateEServiceById(eServiceId: UUID, updateEServiceSeed: UpdateEServiceSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[EService] = withHeaders { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[EService] =
      api.updateEServiceById(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        updateEServiceSeed = updateEServiceSeed,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(request, s"Updating EService with $eServiceId")
  }

  override def getEServiceDocumentById(eServiceId: UUID, descriptorId: UUID, documentId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[EServiceDoc] = withHeaders { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[EServiceDoc] =
      api.getEServiceDocumentById(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        documentId = documentId,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(
      request,
      s"Retrieving document ${documentId.toString} of EService ${eServiceId.toString} from Catalog Process"
    )
  }

  override def deleteDraft(eServiceId: UUID, descriptorId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[Unit] =
      api.deleteDraft(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(request, s"Deleting draft descriptor ${descriptorId.toString} for E-Service ${eServiceId.toString}")
  }

  override def deleteEService(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Unit] =
        api.deleteEService(xCorrelationId = correlationId, eServiceId = eServiceId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Deleting E-Service $eServiceId")
    }

  override def getEServiceConsumers(eServiceId: UUID, offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[EServiceConsumers] = withHeaders { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[EServiceConsumers] =
      api.getEServiceConsumers(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        offset = offset,
        limit = limit,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(request, s"Retrieving consumers for EService ${eServiceId.toString} from Catalog Process")
  }

  override def createRiskAnalysis(eServiceId: UUID, eServiceRiskAnalysisSeed: EServiceRiskAnalysisSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[Unit] =
      api.createRiskAnalysis(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        eServiceRiskAnalysisSeed = eServiceRiskAnalysisSeed,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(
      request,
      s"Create Risk Analysis with name ${eServiceRiskAnalysisSeed.name} for EService ${eServiceId.toString} from Catalog Process"
    )
  }

  override def updateRiskAnalysis(
    eServiceId: UUID,
    riskAnalysisId: UUID,
    eServiceRiskAnalysisSeed: EServiceRiskAnalysisSeed
  )(implicit contexts: Seq[(String, String)]) = withHeaders { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[Unit] =
      api.updateRiskAnalysis(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        riskAnalysisId = riskAnalysisId,
        eServiceRiskAnalysisSeed = eServiceRiskAnalysisSeed,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(
      request,
      s"Update Risk Analysis ${riskAnalysisId.toString} for EService ${eServiceId.toString} from Catalog Process"
    )
  }

  override def deleteRiskAnalysis(eServiceId: UUID, riskAnalysisId: UUID)(implicit contexts: Seq[(String, String)]) =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Unit] =
        api.deleteRiskAnalysis(
          xCorrelationId = correlationId,
          eServiceId = eServiceId,
          riskAnalysisId = riskAnalysisId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(
        request,
        s"Delete Risk Analysis ${riskAnalysisId.toString} for EService ${eServiceId.toString} from Catalog Process"
      )
    }
}
