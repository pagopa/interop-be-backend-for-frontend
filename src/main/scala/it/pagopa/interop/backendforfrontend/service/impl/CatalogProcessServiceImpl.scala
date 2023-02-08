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

  def activateDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId, ip) =>
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
    states: Seq[EServiceDescriptorState],
    offset: Int,
    limit: Int
  )(implicit contexts: Seq[(String, String)]): Future[EServices] =
    withHeaders[EServices] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[EServices] = api.getEServices(
        name = name,
        eservicesIds = eServicesIds,
        producersIds = producersIds,
        states = states,
        offset = offset,
        limit = limit,
        xCorrelationId = correlationId,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      invoker.invoke(
        request,
        s"Retrieving EServices for name = $name, producersIds = $producersIds, states = $states, offset = $offset, limit = $limit,"
      )
    }

  override def getEServiceById(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[EService] =
        api.getEServiceById(xCorrelationId = correlationId, eServiceId = eServiceId.toString, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Retrieving EService for $eServiceId from Catalog Process")
    }

  def createDescriptor(eServiceId: UUID, eServiceDescriptorSeed: EServiceDescriptorSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[EServiceDescriptor] = {
    withHeaders { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[EServiceDescriptor] =
        api.createDescriptor(
          xCorrelationId = correlationId,
          eServiceId = eServiceId.toString,
          eServiceDescriptorSeed = eServiceDescriptorSeed,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Create descriptor for $eServiceId ")
    }
  }

  def publishDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[Unit] =
      api.publishDescriptor(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(request, s"Publishing Descriptor $descriptorId EService for $eServiceId from Catalog Process")
  }

  override def suspendDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders { (bearerToken, correlationId, ip) =>
    val request: ApiRequest[Unit] =
      api.suspendDescriptor(
        xCorrelationId = correlationId,
        eServiceId = eServiceId,
        descriptorId = descriptorId,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(request, s"Retrieving EService for $eServiceId from Catalog Process")
  }

  def updateEServiceDocumentById(
    eServiceId: String,
    descriptorId: String,
    documentId: String,
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
      s"Updating document $documentId on eService $eServiceId for descriptor $descriptorId with seed $updateEServiceDescriptorDocumentSeed from Catalog Process"
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
    invoker.invoke(request, s"Cloning EService $eServiceId with descriptor $descriptorId")
  }

  override def deleteEServiceDocumentById(eServiceId: String, descriptorId: String, documentId: String)(implicit
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
    invoker.invoke(request, s"Deleting document $documentId on eService $eServiceId for descriptor $descriptorId")
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
          eServiceId = eServiceId.toString,
          descriptorId = descriptorId.toString,
          updateEServiceDescriptorSeed = updateEServiceDescriptorSeed,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(
        request,
        s"Update draft descriptor $descriptorId for EService $eServiceId with seed $updateEServiceDescriptorSeed"
      )
    }
}
