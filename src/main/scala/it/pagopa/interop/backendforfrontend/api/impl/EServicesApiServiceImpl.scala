package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.ContentDispositionTypes.attachment
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import io.circe.syntax._
import io.circe.{Json, JsonObject}
import it.pagopa.interop.agreementprocess.client.{model => AgreementProcess}
import it.pagopa.interop.agreementprocess.lifecycle.AttributesRules.certifiedAttributesSatisfied
import it.pagopa.interop.backendforfrontend.api.EservicesApiService
import it.pagopa.interop.backendforfrontend.api.impl.Utils.{
  assertRequesterAllowed,
  canBeUpgraded,
  generateUniqueFileName,
  pollEServiceById,
  verifyAndCreateEServiceDocument
}
import it.pagopa.interop.backendforfrontend.common.HeaderUtils._
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.BFFErrors._
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service._
import it.pagopa.interop.backendforfrontend.service.model.{ImportedDoc, ImportedEservice}
import it.pagopa.interop.backendforfrontend.service.types.AgreementProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.CatalogProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.TenantProcessServiceTypes._
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils._
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.tenantprocess.client.{model => TenantProcess}
import org.apache.tika.Tika
import spray.json._

import java.io._
import java.net.URI
import java.nio.file.{Files, Path, Paths}
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.jdk.StreamConverters.StreamHasToScala
import scala.util.{Success, Try, Using}

final case class EServicesApiServiceImpl(
  agreementProcessService: AgreementProcessService,
  attributeRegistryProcessService: AttributeRegistryProcessService,
  catalogProcessService: CatalogProcessService,
  tenantProcessService: TenantProcessService,
  selfcareV2ClientService: SelfcareV2ClientService,
  fileManager: FileManager,
  uuidSupplier: UUIDSupplier,
  offsetDateTimeSupplier: OffsetDateTimeSupplier
)(implicit ec: ExecutionContext)
    extends EservicesApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private val ACTIVE_DESCRIPTOR_STATES_FILTER: List[CatalogProcess.EServiceDescriptorState] = List(
    CatalogProcess.EServiceDescriptorState.PUBLISHED,
    CatalogProcess.EServiceDescriptorState.SUSPENDED,
    CatalogProcess.EServiceDescriptorState.DEPRECATED
  )

  private val SUBSCRIBED_AGREEMENT_STATES: Set[AgreementProcess.AgreementState] = Set(
    AgreementProcess.AgreementState.PENDING,
    AgreementProcess.AgreementState.ACTIVE,
    AgreementProcess.AgreementState.SUSPENDED
  )

  override def createEService(eServiceSeed: EServiceSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {
    val result: Future[CreatedResource] =
      catalogProcessService.createEService(eServiceSeed.toProcess)(contexts, ec).map(_.toApi)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error creating eservice with seed: $eServiceSeed", headers) orElse { case Success(eservice) =>
        createEService200(headers)(eservice)
      }
    }
  }

  override def activateDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Unit] = for {
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      _              <- catalogProcessService.activateDescriptor(eServiceUuid, descriptorUuid)(contexts)
    } yield ()

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error activating descriptor $descriptorId on eservice $eServiceId", headers) orElse {
        case Success(_) =>
          activateDescriptor204(headers)
      }
    }
  }

  override def publishDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Unit] = for {
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      _              <- catalogProcessService.publishDescriptor(eServiceUuid, descriptorUuid)(contexts)
    } yield ()

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error publishing descriptor $descriptorId for eservice $eServiceId", headers) orElse {
        case Success(_) =>
          publishDescriptor204(headers)
      }
    }
  }

  override def createDescriptor(eServiceId: String, eServiceDescriptorSeed: EServiceDescriptorSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {
    val result: Future[CreatedResource] = for {
      eServiceUuid <- eServiceId.toFutureUUID
      descriptor   <- catalogProcessService.createDescriptor(eServiceUuid, eServiceDescriptorSeed.toProcess)(contexts)
    } yield descriptor.toApi

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error creating descriptor with seed: $eServiceDescriptorSeed", headers) orElse {
        case Success(descriptor) =>
          createDescriptor200(headers)(descriptor)
      }
    }
  }

  override def updateDescriptor(eServiceId: String, descriptorId: String, seed: UpdateEServiceDescriptorQuotas)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {
    val result = for {
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      descriptor     <- catalogProcessService.updateDescriptor(eServiceUuid, descriptorUuid, seed.toProcess)(contexts)
    } yield descriptor.toApi

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error updating descriptor $descriptorId on service $eServiceId with seed: $seed", headers) orElse {
        case Success(resource) =>
          updateDescriptor200(headers)(resource)
      }
    }
  }

  override def updateDraftDescriptor(
    eServiceId: String,
    descriptorId: String,
    updateEServiceDescriptorSeed: UpdateEServiceDescriptorSeed
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {
    val result = for {
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      descriptor     <- catalogProcessService.updateDraftDescriptor(
        eServiceUuid,
        descriptorUuid,
        updateEServiceDescriptorSeed.toProcess
      )(contexts)
    } yield descriptor.toApi

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error updating draft descriptor $descriptorId on service $eServiceId with seed: $updateEServiceDescriptorSeed",
        headers
      ) orElse { case Success(resource) =>
        updateDraftDescriptor200(headers)(resource)
      }
    }
  }

  override def getEServicesCatalog(
    q: Option[String],
    producersIds: String,
    attributesIds: String,
    states: String,
    agreementStates: String,
    mode: Option[String],
    offset: Int,
    limit: Int
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCatalogEServices: ToEntityMarshaller[CatalogEServices]
  ): Route = {
    val result: Future[CatalogEServices] = for {
      requesterUuid      <- getOrganizationIdFutureUUID(contexts)
      apiStates          <- parseArrayParameters(states).traverse(EServiceDescriptorState.fromValue).toFuture
      mode               <- mode.traverse(EServiceMode.fromValue).toFuture
      apiAgreementStates <- parseArrayParameters(agreementStates).traverse(AgreementState.fromValue).toFuture
      producersUuids     <- parseArrayParameters(producersIds).traverse(_.toFutureUUID)
      attributesUuids    <- parseArrayParameters(attributesIds).traverse(_.toFutureUUID)
      pagedResults       <- catalogProcessService.getEServices(
        name = q,
        eServicesIds = Nil,
        producersIds = producersUuids,
        attributesIds = attributesUuids,
        agreementStates = apiAgreementStates.map(CatalogProcess.AgreementState.fromApi),
        states = apiStates.map(CatalogProcess.EServiceDescriptorState.fromApi),
        mode = mode.map(CatalogProcess.EServiceMode.fromApi),
        offset = offset,
        limit = limit
      )
      enhancedEServices  <- Future.traverse(pagedResults.results)(enhanceCatalogEService(requesterUuid))
    } yield CatalogEServices(
      results = enhancedEServices,
      pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
    )

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving Catalog EServices", headers) orElse { case Success(eServices) =>
        getEServicesCatalog200(headers)(eServices)
      }
    }
  }

  override def getCatalogEServiceDescriptor(eserviceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCatalogEServiceDescriptor: ToEntityMarshaller[CatalogEServiceDescriptor]
  ): Route = {
    val result: Future[CatalogEServiceDescriptor] = for {
      requesterId                    <- getOrganizationIdFutureUUID(contexts)
      (eserviceUUID, descriptorUUID) <- eserviceId.toFutureUUID.zip(descriptorId.toFutureUUID)
      eService                       <- catalogProcessService.getEServiceById(eserviceUUID)
      descriptor                     <- eService.descriptors
        .find(_.id === descriptorUUID)
        .toFuture(EServiceDescriptorNotFound(eService.id.toString, descriptorId))
      attributes                     <- attributeRegistryProcessService
        .getBulkAttributes(extractIdsFromAttributes(descriptor.attributes))
      descriptorAttributes           <- descriptor.attributes.toApi(attributes)
      requesterTenant                <- tenantProcessService.getTenant(requesterId)
      producerTenant                 <- tenantProcessService.getTenant(eService.producerId)
      agreement                      <- agreementProcessService.getLatestAgreement(requesterId, eService)
    } yield CatalogEServiceDescriptor(
      id = descriptor.id,
      version = descriptor.version,
      description = descriptor.description,
      interface = descriptor.interface.map(_.toApi),
      docs = descriptor.docs.map(_.toApi),
      state = descriptor.state.toApi,
      audience = descriptor.audience,
      voucherLifespan = descriptor.voucherLifespan,
      dailyCallsPerConsumer = descriptor.dailyCallsPerConsumer,
      dailyCallsTotal = descriptor.dailyCallsTotal,
      agreementApprovalPolicy = descriptor.agreementApprovalPolicy.toApi,
      attributes = descriptorAttributes,
      eservice = CatalogDescriptorEService(
        id = eService.id,
        name = eService.name,
        producer = CompactOrganization(
          id = producerTenant.id,
          name = producerTenant.name,
          kind = producerTenant.kind.map(_.toApi)
        ),
        description = eService.description,
        technology = eService.technology.toApi,
        descriptors = getNonDraftDescriptors(eService).map(_.toCompactDescriptor),
        agreement = agreement.map { a =>
          CompactAgreement(id = a.id, state = a.state.toApi, canBeUpgraded = canBeUpgraded(eService, a))
        },
        isMine = eService.producerId == requesterId,
        hasCertifiedAttributes = certifiedAttributesSatisfied(
          descriptor.attributes.toPersistent,
          consumerAttributes = requesterTenant.attributes.mapFilter(_.certified).map(_.toPersistent)
        ),
        isSubscribed = agreement.exists(a => SUBSCRIBED_AGREEMENT_STATES.contains(a.state)),
        activeDescriptor =
          getActiveDescriptor(eService).map(ad => CompactDescriptor(ad.id, ad.state.toApi, ad.version, ad.audience)),
        mail = producerTenant.mails.find(_.kind == TenantProcess.MailKind.CONTACT_EMAIL).map(_.toApi),
        mode = eService.mode.toApi,
        riskAnalysis = eService.riskAnalysis.map(_.toApi)
      ),
      publishedAt = descriptor.publishedAt,
      suspendedAt = descriptor.suspendedAt,
      deprecatedAt = descriptor.deprecatedAt,
      archivedAt = descriptor.archivedAt
    )

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving descriptor $descriptorId of eservice $eserviceId from catalog", headers) orElse {
        case Success(descriptor) => getCatalogEServiceDescriptor200(headers)(descriptor)
      }
    }
  }

  override def addRiskAnalysisToEService(
    eServiceId: String,
    eServiceRiskAnalysisSeed: EServiceRiskAnalysisSeed
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    val result: Future[Unit] = for {
      eserviceUUID <- eServiceId.toFutureUUID
      _            <- catalogProcessService.createRiskAnalysis(eserviceUUID, eServiceRiskAnalysisSeed.toProcess)
    } yield ()

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error inserting risk analysis ${eServiceRiskAnalysisSeed.name} to eservice $eServiceId from catalog",
        headers
      ) orElse { case Success(_) =>
        addRiskAnalysisToEService204(headers)
      }
    }
  }

  override def deleteEServiceRiskAnalysis(eServiceId: String, riskAnalysisId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Unit] = for {
      eserviceUUID     <- eServiceId.toFutureUUID
      riskAnalysisUUID <- riskAnalysisId.toFutureUUID
      _                <- catalogProcessService.deleteRiskAnalysis(eserviceUUID, riskAnalysisUUID)
    } yield ()

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error deleting risk analysis ${riskAnalysisId} to eservice $eServiceId from catalog",
        headers
      ) orElse { case Success(_) =>
        deleteEServiceRiskAnalysis204(headers)
      }
    }
  }

  override def updateEServiceRiskAnalysis(
    eServiceId: String,
    riskAnalysisId: String,
    eServiceRiskAnalysisSeed: EServiceRiskAnalysisSeed
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    val result: Future[Unit] = for {
      eserviceUUID     <- eServiceId.toFutureUUID
      riskAnalysisUUID <- riskAnalysisId.toFutureUUID
      _ <- catalogProcessService.updateRiskAnalysis(eserviceUUID, riskAnalysisUUID, eServiceRiskAnalysisSeed.toProcess)
    } yield ()

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error updating risk analysis ${riskAnalysisId} to eservice $eServiceId from catalog",
        headers
      ) orElse { case Success(_) =>
        updateEServiceRiskAnalysis204(headers)
      }
    }
  }

  override def getEServiceRiskAnalysis(eServiceId: String, riskAnalysisId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServiceRiskAnalysis: ToEntityMarshaller[EServiceRiskAnalysis],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[EServiceRiskAnalysis] = for {
      eserviceUUID     <- eServiceId.toFutureUUID
      riskAnalysisUUID <- riskAnalysisId.toFutureUUID
      eService         <- catalogProcessService.getEServiceById(eserviceUUID)
      riskAnalysis     <- eService.riskAnalysis
        .find(_.id == riskAnalysisUUID)
        .toFuture(EServiceRiskAnalysisNotFound(eserviceUUID, riskAnalysisUUID))
    } yield riskAnalysis.toApi

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error retrieving risk analysis ${riskAnalysisId} to eservice $eServiceId from catalog",
        headers
      ) orElse { case Success(riskAnalysis) =>
        getEServiceRiskAnalysis200(headers)(riskAnalysis)
      }
    }
  }

  private def extractIdsFromAttributes(attributes: CatalogProcess.Attributes): Seq[UUID] =
    attributes.certified.flatMap(_.map(_.id)) ++
      attributes.declared.flatMap(_.map(_.id)) ++
      attributes.verified.flatMap(_.map(_.id))

  override def getProducerEServices(q: Option[String], consumersIds: String, offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProducerEServices: ToEntityMarshaller[ProducerEServices],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    def getResults(producerId: UUID, eServicesIds: List[UUID]): Future[CatalogProcess.EServices] =
      catalogProcessService.getEServices(
        name = q,
        eServicesIds = eServicesIds,
        producersIds = List(producerId),
        attributesIds = Nil,
        agreementStates = Nil,
        states = Nil,
        mode = None,
        offset = offset,
        limit = limit
      )

    val result: Future[ProducerEServices] = for {
      producerId    <- getOrganizationIdFutureUUID(contexts)
      consumerUUIDs <- Future.traverse(parseArrayParameters(consumersIds))(_.toFutureUUID)
      pagedResults  <-
        if (consumerUUIDs.isEmpty) getResults(producerId, Nil)
        else
          getProducerEServicesIds(producerId, consumerUUIDs).flatMap {
            case Nil => Future.successful(CatalogProcess.EServices(Nil, 0))
            case xs  => getResults(producerId, xs)
          }
    } yield ProducerEServices(
      results = pagedResults.results.map(enhanceProducerEService),
      pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
    )

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving Producer EServices", headers) orElse { case Success(eServices) =>
        getProducerEServices200(headers)(eServices)
      }
    }
  }

  private def getProducerEServicesIds(producerId: UUID, consumersUUIDs: List[UUID])(implicit
    contexts: Seq[(String, String)]
  ): Future[List[UUID]] =
    agreementProcessService
      .getAllAgreements(
        producersIds = producerId :: Nil,
        consumersIds = consumersUUIDs,
        eServicesIds = Nil,
        states = List(AgreementProcess.AgreementState.ACTIVE, AgreementProcess.AgreementState.SUSPENDED)
      )
      .map(_.map(_.eserviceId).distinct)

  private def enhanceCatalogEService(
    requesterId: UUID
  )(eService: CatalogProcess.EService)(implicit contexts: Seq[(String, String)]): Future[CatalogEService] = for {
    producerTenant  <- tenantProcessService.getTenant(eService.producerId)
    requesterTenant <-
      if (requesterId != eService.producerId) tenantProcessService.getTenant(requesterId)
      else Future.successful(producerTenant)
    activeDescriptor = getActiveDescriptor(eService)
    latestAgreement <- agreementProcessService.getLatestAgreement(requesterId, eService)
    hasCertifiedAttributes = activeDescriptor.fold(false)(descriptor =>
      certifiedAttributesSatisfied(
        descriptor.attributes.toPersistent,
        requesterTenant.attributes.mapFilter(_.certified).map(_.toPersistent)
      )
    )
  } yield CatalogEService(
    id = eService.id,
    name = eService.name,
    description = eService.description,
    producer = CompactOrganization(id = eService.producerId, name = producerTenant.name),
    agreement = latestAgreement.map(a =>
      CompactAgreement(id = a.id, state = a.state.toApi, canBeUpgraded = canBeUpgraded(eService, a))
    ),
    isMine = eService.producerId == requesterId,
    hasCertifiedAttributes = hasCertifiedAttributes,
    activeDescriptor = activeDescriptor.map(_.toCompactDescriptor)
  )

  private def enhanceProducerEService(eService: CatalogProcess.EService) = ProducerEService(
    id = eService.id,
    name = eService.name,
    mode = eService.mode.toApi,
    activeDescriptor = getActiveDescriptor(eService).map(_.toCompactDescriptor),
    draftDescriptor = getDraftDescriptor(eService).map(_.toCompactDescriptor)
  )

  override def getProducerEServiceDetails(eserviceId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProducerEServiceDetails: ToEntityMarshaller[ProducerEServiceDetails],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[ProducerEServiceDetails] = for {
      requesterId  <- getOrganizationIdFutureUUID(contexts)
      eserviceUUID <- eserviceId.toFutureUUID
      eService     <- catalogProcessService.getEServiceById(eserviceUUID)
      // This is an anti-pattern, but it's the cleanest solution
      _            <- isTheProducer(eService, requesterId)
    } yield ProducerEServiceDetails(
      id = eService.id,
      name = eService.name,
      description = eService.description,
      technology = eService.technology.toApi,
      mode = eService.mode.toApi,
      riskAnalysis = eService.riskAnalysis.map(_.toApi)
    )

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving producer eservice $eserviceId", headers) orElse { case Success(eService) =>
        getProducerEServiceDetails200(headers)(eService)
      }
    }
  }

  override def getProducerEServiceDescriptor(eserviceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerProducerDescriptor: ToEntityMarshaller[ProducerEServiceDescriptor]
  ): Route = {
    val result: Future[ProducerEServiceDescriptor] = for {
      requesterId                    <- getOrganizationIdFutureUUID(contexts)
      (eserviceUUID, descriptorUUID) <- eserviceId.toFutureUUID.zip(descriptorId.toFutureUUID)
      eService                       <- catalogProcessService.getEServiceById(eserviceUUID)
      // This is an anti-pattern, but it's the cleanest solution
      _                              <- isTheProducer(eService, requesterId)
      descriptor                     <- eService.descriptors
        .find(_.id === descriptorUUID)
        .toFuture(EServiceDescriptorNotFound(eService.id.toString, descriptorId))
      attributes                     <- attributeRegistryProcessService
        .getBulkAttributes(extractIdsFromAttributes(descriptor.attributes))
      descriptorAttributes           <- descriptor.attributes.toApi(attributes)
      requesterTenant                <- tenantProcessService.getTenant(eService.producerId)
    } yield ProducerEServiceDescriptor(
      id = descriptor.id,
      version = descriptor.version,
      description = descriptor.description,
      interface = descriptor.interface.map(_.toApi),
      docs = descriptor.docs.map(_.toApi),
      state = descriptor.state.toApi,
      audience = descriptor.audience,
      voucherLifespan = descriptor.voucherLifespan,
      dailyCallsPerConsumer = descriptor.dailyCallsPerConsumer,
      dailyCallsTotal = descriptor.dailyCallsTotal,
      agreementApprovalPolicy = descriptor.agreementApprovalPolicy.toApi,
      attributes = descriptorAttributes,
      eservice = ProducerDescriptorEService(
        id = eService.id,
        name = eService.name,
        description = eService.description,
        technology = eService.technology.toApi,
        descriptors = getNonDraftDescriptors(eService).map(_.toCompactDescriptor),
        draftDescriptor =
          getDraftDescriptor(eService).map(ad => CompactDescriptor(ad.id, ad.state.toApi, ad.version, ad.audience)),
        mail = requesterTenant.mails.find(_.kind == TenantProcess.MailKind.CONTACT_EMAIL).map(_.toApi),
        mode = eService.mode.toApi,
        riskAnalysis = eService.riskAnalysis.map(_.toApi)
      )
    )

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving producer descriptor $descriptorId of eservice $eserviceId", headers) orElse {
        case Success(descriptor) => getProducerEServiceDescriptor200(headers)(descriptor)
      }
    }
  }

  override def createEServiceDocument(
    kind: String,
    prettyName: String,
    doc: (FileInfo, File),
    eServiceId: String,
    descriptorId: String
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {

    val result: Future[CreatedResource] = for {
      (eserviceUUID, descriptorUUID) <- eServiceId.toFutureUUID.zip(descriptorId.toFutureUUID)
      eService                       <- catalogProcessService.getEServiceById(eserviceUUID)
      _                              <- eService.descriptors
        .find(_.id === descriptorUUID)
        .toFuture(EServiceDescriptorNotFound(eService.id.toString, descriptorId))
      documentIdUuid = uuidSupplier.get()
      _ <- verifyAndCreateEServiceDocument(
        catalogProcessService,
        fileManager,
        eService,
        doc,
        prettyName,
        kind,
        descriptorUUID,
        documentIdUuid
      )
    } yield CreatedResource(documentIdUuid)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error creating eService document of kind $kind and name $prettyName for eService $eServiceId and descriptor $descriptorId",
        headers
      ) orElse { case Success(resource) =>
        createEServiceDocument200(headers)(resource)
      }
    }
  }

  override def updateEServiceDocumentById(
    eServiceId: String,
    descriptorId: String,
    documentId: String,
    updateEServiceDescriptorDocumentSeed: UpdateEServiceDescriptorDocumentSeed
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServiceDoc: ToEntityMarshaller[EServiceDoc],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[EServiceDoc] = for {
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      documentUuid   <- documentId.toFutureUUID
      document       <- catalogProcessService.updateEServiceDocumentById(
        eServiceId = eServiceUuid,
        descriptorId = descriptorUuid,
        documentId = documentUuid,
        updateEServiceDescriptorDocumentSeed = updateEServiceDescriptorDocumentSeed.toProcess
      )(contexts)
    } yield document.toApi

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error updating document $documentId on eService $eServiceId for descriptor $descriptorId",
        headers
      ) orElse { case Success(eServiceDoc) =>
        updateEServiceDocumentById200(headers)(eServiceDoc)
      }
    }
  }

  private def isTheProducer(eService: CatalogProcess.EService, requesterId: UUID): Future[Unit] =
    Future.failed(InvalidEServiceRequester(eService.id, requesterId)).unlessA(eService.producerId == requesterId)

  private def getActiveDescriptor(eService: CatalogProcess.EService): Option[CatalogProcess.EServiceDescriptor] =
    eService.descriptors
      .filter(d => ACTIVE_DESCRIPTOR_STATES_FILTER.contains(d.state))
      .sortBy(_.version.toInt)
      .lastOption

  private def getDraftDescriptor(eService: CatalogProcess.EService): Option[CatalogProcess.EServiceDescriptor] =
    eService.descriptors.find(_.state == CatalogProcess.EServiceDescriptorState.DRAFT)

  private def getNonDraftDescriptors(eService: CatalogProcess.EService): Seq[CatalogProcess.EServiceDescriptor] =
    eService.descriptors.filter(_.state != CatalogProcess.EServiceDescriptorState.DRAFT)

  override def suspendDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Unit] = for {
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      _              <- catalogProcessService.suspendDescriptor(eServiceUuid, descriptorUuid)(contexts)
    } yield ()

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error suspending descriptor $descriptorId", headers) orElse { case Success(_) =>
        suspendDescriptor204(headers)
      }
    }
  }

  override def cloneEServiceByDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedEServiceDescriptor]
  ): Route = {
    val result: Future[CreatedEServiceDescriptor] = for {
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      eservice       <- catalogProcessService.cloneEServiceByDescriptor(eServiceUuid, descriptorUuid)
      descriptorId   <- eservice.descriptors.headOption.map(_.id).toFuture(NoDescriptorInEservice(eServiceUuid))
    } yield eservice.toApiWithDescriptorId(descriptorId)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error cloning EService ${eServiceId} with descriptor ${descriptorId}", headers) orElse {
        case Success(eservice) =>
          cloneEServiceByDescriptor200(headers)(eservice)
      }
    }
  }

  override def deleteEServiceDocumentById(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Unit] = for {
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      documentUuid   <- documentId.toFutureUUID
      _ <- catalogProcessService.deleteEServiceDocumentById(eServiceUuid, descriptorUuid, documentUuid)(contexts)
    } yield ()

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(
        s"Error deleting document $documentId for eService $eServiceId descriptor $descriptorId",
        headers
      ) orElse { case Success(_) =>
        deleteEServiceDocumentById204(headers)
      }
    }
  }

  override def updateEServiceById(eServiceId: String, updateEServiceSeed: UpdateEServiceSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {
    val result: Future[CreatedResource] = for {
      eServiceUuid <- eServiceId.toFutureUUID
      eService     <- catalogProcessService.updateEServiceById(eServiceUuid, updateEServiceSeed.toProcess)(contexts)
    } yield eService.toApi

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error updating eservice with Id: $eServiceId", headers) orElse { case Success(eservice) =>
        updateEServiceById200(headers)(eservice)
      }
    }
  }

  private def getDocumentContentType(document: CatalogProcess.EServiceDoc): Future[ContentType] =
    ContentType
      .parse(document.contentType)
      .fold(
        ex => Future.failed(ContentTypeParsingError(document.contentType, document.path, ex.map(_.formatPretty))),
        Future.successful
      )

  override def getEServiceDocumentById(eServiceId: String, descriptorId: String, documentId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerFile: ToEntityMarshaller[File]
  ): Route = {
    val result: Future[HttpEntity.Strict] = for {
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      documentUuid   <- documentId.toFutureUUID
      document       <- catalogProcessService.getEServiceDocumentById(eServiceUuid, descriptorUuid, documentUuid)
      contentType    <- getDocumentContentType(document)
      byteStream     <- fileManager.get(ApplicationConfiguration.eServiceDocumentsContainer)(document.path)
    } yield HttpEntity(contentType, byteStream.toByteArray())

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error getting document $documentId of eservice $eServiceId", headers) orElse {
        case Success(documentEntity) =>
          complete(StatusCodes.OK, headers, documentEntity)
      }
    }
  }

  override def deleteDraft(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    val result: Future[Unit] = for {
      eServiceUuid   <- eServiceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      _              <- catalogProcessService.deleteDraft(eServiceUuid, descriptorUuid)
    } yield ()

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error while deleting draft descriptor $descriptorId for E-Service $eServiceId", headers) orElse {
        case Success(_) =>
          deleteDraft204(headers)
      }
    }
  }

  override def deleteEService(
    eServiceId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    val result = for {
      eServiceUuid <- eServiceId.toFutureUUID
      _            <- catalogProcessService.deleteEService(eServiceUuid)
    } yield ()

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error while deleting E-Service $eServiceId", headers) orElse { case Success(_) =>
        deleteEService204(headers)
      }
    }
  }

  override def getEServiceConsumers(eServiceId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerFile: ToEntityMarshaller[File]
  ): Route = {

    val dtf: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss")

    def getLines(consumers: Seq[EServiceConsumer]): Array[Byte] = {
      val header             = "versione,stato_versione,stato_richiesta_fruizione,fruitore,codice_ipa_fruitore"
      val lines: Seq[String] = Seq(header) ++ consumers.map { c =>
        List(
          c.descriptorVersion.toString,
          c.descriptorState.toString,
          c.agreementState.toString,
          c.consumerName,
          c.consumerExternalId
        ).map(s => s"\"$s\"").mkString(",")
      }
      lines.mkString("\n").getBytes()
    }

    val result: Future[(String, HttpEntity.Strict)] = for {
      eServiceUUID <- eServiceId.toFutureUUID
      eService     <- catalogProcessService.getEServiceById(eServiceUUID)
      consumers    <- catalogProcessService.getAllEServiceConsumers(eServiceUUID)
      filename     = s"${offsetDateTimeSupplier.get().format(dtf)}-lista-fruitori-${eService.name}.csv"
      apiConsumers = consumers.map(_.toApi)
      byteStream   = getLines(apiConsumers)
    } yield (filename, HttpEntity(ContentType(MediaTypes.`application/octet-stream`), byteStream))

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error getting consumers of eservice $eServiceId", headers) orElse {
        case Success((filename, resource)) =>
          complete(
            StatusCodes.OK,
            headers ++ Seq(`Content-Disposition`(attachment, Map("filename" -> filename))),
            resource
          )
      }
    }
  }

  override def exportEServiceDescriptor(eserviceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerFileResource: ToEntityMarshaller[FileResource],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    def extractConfig(eService: CatalogProcess.EService, descriptor: CatalogProcess.EServiceDescriptor): Json = {
      val nameCounts = mutable.Map[String, Int]()

      JsonObject(
        "name"         -> Json.fromString(eService.name),
        "description"  -> Json.fromString(eService.description),
        "technology"   -> Json.fromString(eService.technology.toApi.toString),
        "mode"         -> Json.fromString(eService.mode.toApi.toString),
        "descriptor"   -> Json.obj(
          "interface"               -> descriptor.interface
            .map(interface =>
              Json.obj("prettyName" -> Json.fromString(interface.prettyName), "path" -> Json.fromString(interface.name))
            )
            .getOrElse(Json.Null),
          "docs"                    -> Json.arr(
            descriptor.docs.map(doc => {
              val newName = generateUniqueFileName(nameCounts, doc.name)
              Json
                .obj("prettyName" -> Json.fromString(doc.prettyName), "path" -> Json.fromString(s"documents/$newName"))
            }): _* // `: _*` is used to convert Seq to varargs for Json.arr
          ),
          "audience"                -> Json.arr(descriptor.audience.map(Json.fromString): _*),
          "voucherLifespan"         -> Json.fromInt(descriptor.voucherLifespan),
          "dailyCallsPerConsumer"   -> Json.fromInt(descriptor.dailyCallsPerConsumer),
          "dailyCallsTotal"         -> Json.fromInt(descriptor.dailyCallsTotal),
          "description"             -> descriptor.description.map(Json.fromString).getOrElse(Json.Null),
          "agreementApprovalPolicy" -> Json.fromString(descriptor.agreementApprovalPolicy.toApi.toString)
        ),
        "riskAnalysis" -> Json.arr(
          eService.riskAnalysis.map(ra =>
            Json.obj(
              "name"             -> Json.fromString(ra.name),
              "riskAnalysisForm" -> Json.obj(
                "version"       -> Json.fromString(ra.riskAnalysisForm.version),
                "singleAnswers" -> Json.arr(
                  ra.riskAnalysisForm.singleAnswers.map(sa =>
                    Json.obj(
                      "key"   -> Json.fromString(sa.key),
                      "value" -> sa.value.map(Json.fromString).getOrElse(Json.Null)
                    )
                  ): _*
                ),
                "multiAnswers"  -> Json.arr(
                  ra.riskAnalysisForm.multiAnswers.map(ma =>
                    Json.obj("key" -> Json.fromString(ma.key), "values" -> Json.arr(ma.values.map(Json.fromString): _*))
                  ): _*
                )
              )
            )
          ): _*
        )
      ).asJson
    }

    def verifyExportEligibility(descriptor: CatalogProcess.EServiceDescriptor): Future[Unit] =
      descriptor.state match {
        case CatalogProcess.EServiceDescriptorState.DRAFT =>
          Future.failed(NotValidDescriptor(descriptor.id.toString, descriptor.state.toString))
        case _                                            => Future.unit
      }

    def createZip(
      folderName: String,
      docs: Seq[(Array[Byte], String)],
      interfaceFile: (Array[Byte], String),
      config: Json
    ): Array[Byte] = {
      val bos        = new ByteArrayOutputStream()
      val zipOut     = new ZipOutputStream(bos)
      val nameCounts = mutable.Map[String, Int]()

      docs.foreach { case (data, entryName) =>
        val newName = generateUniqueFileName(nameCounts, entryName)
        zipOut.putNextEntry(new ZipEntry(s"$folderName/documents/$newName"))
        zipOut.write(data)
        zipOut.closeEntry()
      }

      val (interfaceData, interfaceFileName) = interfaceFile
      zipOut.putNextEntry(new ZipEntry(s"$folderName/$interfaceFileName"))
      zipOut.write(interfaceData)
      zipOut.closeEntry()

      zipOut.putNextEntry(new ZipEntry(s"$folderName/configuration.json"))
      zipOut.write(config.toString.getBytes("UTF-8"))
      zipOut.closeEntry()

      zipOut.finish()
      zipOut.flush()
      bos.flush()
      zipOut.close()
      bos.toByteArray
    }

    val result: Future[FileResource] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      eServiceUuid   <- eserviceId.toFutureUUID
      descriptorUuid <- descriptorId.toFutureUUID
      eService       <- catalogProcessService.getEServiceById(eServiceUuid)
      _              <- assertRequesterAllowed(eService.producerId)(organizationId)
      descriptor     <- eService.descriptors
        .find(_.id === descriptorUuid)
        .toFuture(EServiceDescriptorNotFound(eService.id.toString, descriptorId))
      _              <- verifyExportEligibility(descriptor)
      interface      <- descriptor.interface.toFuture(MissingInterface(eServiceUuid, descriptorUuid))
      interfaceFile  <- fileManager
        .get(ApplicationConfiguration.eServiceDocumentsContainer)(interface.path)
        .map(_.toByteArray)
      docFiles       <- descriptor.docs.traverse(doc =>
        fileManager
          .get(ApplicationConfiguration.eServiceDocumentsContainer)(doc.path)
          .map(filestream => (filestream.toByteArray, doc.name))
      )
      config     = extractConfig(eService, descriptor)
      folderName = s"${eService.id}_${descriptor.id}"
      zipPath    = s"$organizationId/$folderName.zip"
      zipFile    = createZip(folderName, docFiles, (interfaceFile, interface.name), config)
      _          = fileManager.storeBytes(
        ApplicationConfiguration.exportEserviceContainer,
        ApplicationConfiguration.exportEservicePath,
        zipPath
      )(zipFile)
      presignedUrl <- fileManager
        .generateGetPresignedUrl(
          ApplicationConfiguration.exportEserviceContainer,
          ApplicationConfiguration.exportEservicePath,
          zipPath,
          FiniteDuration(ApplicationConfiguration.getUrlDurationMinutes, TimeUnit.MINUTES)
        )
        .toEither
        .toFuture
      presignedURI <- Try(new URI(presignedUrl)).toEither.toFuture
    } yield FileResource(s"$folderName.zip", presignedURI)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error exporting eservice $eserviceId with descriptor $descriptorId", headers) orElse {
        case Success(resource) =>
          exportEServiceDescriptor200(headers)(resource)
      }
    }
  }

  override def getImportEservicePresignedUrl(fileName: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPresignedUrl: ToEntityMarshaller[PresignedUrl],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[PresignedUrl] = for {
      organizationId <- getOrganizationIdFuture(contexts)
      presignedUrl   <- fileManager
        .generatePutPresignedUrl(
          ApplicationConfiguration.importEServiceContainer,
          s"${ApplicationConfiguration.importEServicePath}/$organizationId",
          fileName,
          FiniteDuration(ApplicationConfiguration.putUrlDurationMinutes, TimeUnit.MINUTES)
        )
        .toEither
        .toFuture
      presignedURI   <- Try(new URI(presignedUrl)).toEither.toFuture
    } yield PresignedUrl(presignedURI)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError("Error getting eservice import presigned url", headers) orElse { case Success(resource) =>
        getImportEservicePresignedUrl200(headers)(resource)
      }
    }
  }

  override def importEService(fileResource: FileResource)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedEServiceDescriptor]
  ): Route = {

    def extractZipToTempDirectory(zipBytes: Array[Byte], tenantId: String): Either[Throwable, Path] = {
      Try {
        val tempDir = Files.createTempDirectory(s"${tenantId}_extractedZip")
        Using.resource(new ZipInputStream(new ByteArrayInputStream(zipBytes))) { zipIn =>
          LazyList
            .continually(zipIn.getNextEntry)
            .takeWhile(_ != null)
            .foreach { entry =>
              val filePath = tempDir.resolve(entry.getName)
              if (entry.isDirectory) {
                Files.createDirectories(filePath)
              } else {
                Files.createDirectories(filePath.getParent)
                Using.resource(new BufferedOutputStream(Files.newOutputStream(filePath))) { out =>
                  val buffer = new Array[Byte](1024)
                  Iterator
                    .continually(zipIn.read(buffer))
                    .takeWhile(_ != -1)
                    .foreach(out.write(buffer, 0, _))
                }
              }
            }
        }
        tempDir
      }.toEither
    }

    def checkZipStructure(folderPath: Path): Either[InvalidZipStructure, ImportedEservice] = {
      def fileExists(filePath: Path): Either[InvalidZipStructure, Unit] = {
        Either.cond(Files.exists(filePath), (), InvalidZipStructure(s"File in $filePath not found"))
      }

      for {
        jsonContent      <- Either
          .catchNonFatal(Files.readString(folderPath.resolve("configuration.json")))
          .leftMap(ex => InvalidZipStructure("Error reading configuration.json: " + ex.toString))
        importedEservice <- Either
          .catchNonFatal(jsonContent.parseJson.convertTo[ImportedEservice])
          .leftMap(ex => InvalidZipStructure("Error decoding configuration.json: " + ex.toString))
        _ <- importedEservice.descriptor.docs.foldLeft[Either[InvalidZipStructure, Unit]](Right(())) { (acc, doc) =>
          acc.flatMap(_ => fileExists(folderPath.resolve(doc.path)))
        }
        _ <- importedEservice.descriptor.interface match {
          case Some(interfaceValue) => fileExists(folderPath.resolve(interfaceValue.path))
          case None                 => Right(())
        }
        _ <- {
          val filePaths    = Files.walk(folderPath).skip(1).iterator().asScala.map(_.getFileName.toString).toSet
          val allowedFiles = Set("configuration.json", "documents") ++
            importedEservice.descriptor.docs.map(_.path.split("/").last) ++
            importedEservice.descriptor.interface.map(_.path.split("/").last)

          val extraFiles = filePaths -- allowedFiles
          Either.cond(
            extraFiles.isEmpty,
            (),
            InvalidZipStructure(s"Not allowed files found: ${extraFiles.mkString(", ")}")
          )
        }
      } yield importedEservice
    }

    def deleteTempDirectory(path: Path): Unit = {
      if (Files.isDirectory(path))
        Files.list(path).toScala(Seq).foreach(deleteTempDirectory)
      Files.delete(path)
    }

    def readFileAsBytes(filePath: String): Array[Byte] = {
      Using.resource(new FileInputStream(new File(filePath))) { fileInputStream =>
        val bytes = new Array[Byte](new File(filePath).length.toInt)
        fileInputStream.read(bytes)
        bytes
      }
    }

    def verifyAndCreateImportedDoc(
      eService: CatalogProcess.EService,
      descriptor: CatalogProcess.EServiceDescriptor,
      zipPath: Path,
      file: ImportedDoc,
      fileType: String
    ): Future[Unit] = {
      val filePath = zipPath.resolve(file.path).toString
      val fileName = Paths.get(file.path).getFileName.toString

      val result = for {
        mimeType    <- Try(new Tika().detect(readFileAsBytes(filePath), fileName)).toFuture
        contentType <- ContentType
          .parse(mimeType)
          .leftMap(errors => ContentTypeParsingError(mimeType, file.path, errors.map(_.toString())))
          .toFuture
        fileParts = (FileInfo("", fileName, contentType), new File(filePath))
        _ <- verifyAndCreateEServiceDocument(
          catalogProcessService,
          fileManager,
          eService,
          fileParts,
          file.prettyName,
          fileType,
          descriptor.id,
          uuidSupplier.get()
        )
      } yield ()

      result.recoverWith { case ex: Throwable =>
        deleteTempDirectory(zipPath)
        for {
          _ <- descriptor.docs.traverse { doc =>
            catalogProcessService.deleteEServiceDocumentById(eService.id, descriptor.id, doc.id)
          }
          _ <- descriptor.interface.traverse(interface =>
            catalogProcessService.deleteEServiceDocumentById(eService.id, descriptor.id, interface.id)
          )
          _ <- catalogProcessService.deleteEService(eService.id)
        } yield ()
        Future.failed(ex)
      }
    }

    val result: Future[CreatedEServiceDescriptor] = for {
      tenantId <- getOrganizationIdFuture(contexts)
      zipFile  <- fileManager.getFile(ApplicationConfiguration.importEServiceContainer)(
        s"${ApplicationConfiguration.importEServicePath}/$tenantId/${fileResource.filename}"
      )
      zipPath  <- extractZipToTempDirectory(zipFile, tenantId).toFuture
      listFiles    = Files.list(zipPath).iterator().asScala.toList
      folderPath   = listFiles match {
        case path :: Nil if path.toFile.isDirectory => path
        case _                                      => zipPath
      }
      importedEservice <- checkZipStructure(folderPath).toFuture.recoverWith { case ex: Throwable =>
        deleteTempDirectory(folderPath)
        Future.failed(ex)
      }
      eserviceSeed = CatalogProcess.EServiceSeed(
        name = importedEservice.name,
        description = importedEservice.description,
        technology = importedEservice.technology.toProcess,
        mode = importedEservice.mode.toProcess
      )
      eService   <- catalogProcessService.createEService(eserviceSeed).recoverWith { case ex: Throwable =>
        deleteTempDirectory(folderPath)
        Future.failed(ex)
      }
      _          <- pollEServiceById(catalogProcessService.getEServiceById(eService.id), _ => true)
      _          <- Future.traverse(importedEservice.riskAnalysis) { ra =>
        catalogProcessService
          .createRiskAnalysis(
            eServiceId = eService.id,
            CatalogProcess.EServiceRiskAnalysisSeed(name = ra.name, riskAnalysisForm = ra.riskAnalysisForm.toProcess)
          )
          .recoverWith { case ex: Throwable =>
            deleteTempDirectory(folderPath)
            catalogProcessService.deleteEService(eService.id).flatMap(_ => Future.failed(ex))
          }
          .flatMap(_ => pollEServiceById(catalogProcessService.getEServiceById(eService.id), _.riskAnalysis.nonEmpty))
      }
      descriptorSeed = CatalogProcess.EServiceDescriptorSeed(
        description = importedEservice.descriptor.description,
        audience = importedEservice.descriptor.audience,
        voucherLifespan = importedEservice.descriptor.voucherLifespan,
        dailyCallsPerConsumer = importedEservice.descriptor.dailyCallsPerConsumer,
        dailyCallsTotal = importedEservice.descriptor.dailyCallsTotal,
        agreementApprovalPolicy = importedEservice.descriptor.agreementApprovalPolicy.toProcess,
        attributes = CatalogProcess.AttributesSeed(certified = Seq.empty, declared = Seq.empty, verified = Seq.empty)
      )
      descriptor <- catalogProcessService.createDescriptor(eService.id, descriptorSeed).recoverWith {
        case ex: Throwable =>
          deleteTempDirectory(folderPath)
          catalogProcessService.deleteEService(eService.id).flatMap(_ => Future.failed(ex))
      }
      _          <- pollEServiceById(catalogProcessService.getEServiceById(eService.id), _.descriptors.nonEmpty)
      _          <- importedEservice.descriptor.interface match {
        case Some(interface) =>
          verifyAndCreateImportedDoc(eService, descriptor, folderPath, interface, "INTERFACE")
        case None            => Future.unit
      }
      _          <- pollEServiceById(
        catalogProcessService.getEServiceById(eService.id),
        _.descriptors.exists(d => d.id == descriptor.id && d.interface.isDefined)
      )
      _          <- importedEservice.descriptor.docs
        .traverse(doc =>
          verifyAndCreateImportedDoc(eService, descriptor, folderPath, doc, "DOCUMENT")
            .flatMap(_ =>
              pollEServiceById(
                catalogProcessService.getEServiceById(eService.id),
                _.descriptors.exists(d => d.id == descriptor.id && d.docs.exists(_.prettyName == doc.prettyName))
              )
            )
        )
      _ = deleteTempDirectory(folderPath)
    } yield CreatedEServiceDescriptor(id = eService.id, descriptorId = descriptor.id)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError("Error importing eService", headers) orElse { case Success(resource) =>
        importEService200(headers)(resource)
      }
    }
  }
}
