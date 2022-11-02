package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.lifecycle.AttributesRules.certifiedAttributesSatisfied
import it.pagopa.interop.backendforfrontend.api.EservicesApiService
import it.pagopa.interop.backendforfrontend.error.BFFErrors.MissingSelfcareId
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service.types.AgreementProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.CatalogProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.TenantManagementServiceTypes._
import it.pagopa.interop.backendforfrontend.service.{
  AgreementProcessService,
  CatalogProcessService,
  PartyProcessService,
  TenantManagementService
}
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils._
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import it.pagopa.interop.backendforfrontend.error.BFFErrors._
import akka.http.scaladsl.model.StatusCodes

final case class EServicesApiServiceImpl(
  agreementProcessService: AgreementProcessService,
  catalogProcessService: CatalogProcessService,
  tenantManagementService: TenantManagementService,
  partyProcessService: PartyProcessService
)(implicit ec: ExecutionContext)
    extends EservicesApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private val ACTIVE_DESCRIPTOR_STATES_FILTER: List[CatalogProcess.EServiceDescriptorState] = List(
    CatalogProcess.EServiceDescriptorState.PUBLISHED,
    CatalogProcess.EServiceDescriptorState.SUSPENDED,
    CatalogProcess.EServiceDescriptorState.DEPRECATED
  )

  override def getEserviceDescriptor(eserviceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServiceDescriptor: ToEntityMarshaller[EServiceDescriptor],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[EServiceDescriptor] = for {
      (eserviceUUID, descriptorUUID) <- eserviceId.toFutureUUID.zip(descriptorId.toFutureUUID)
      eService                       <- catalogProcessService.getEServiceById(eserviceUUID)
      descriptor                     <- eService.descriptors
        .find(_.id === descriptorUUID)
        .toFuture(EServiceDescriptorNotFound(eserviceId, descriptorId))
      mail                           <- tenantManagementService
        .getTenant(eService.producer.id)
        .map(_.mails.headOption.map(_.toApi))
    } yield descriptor.toApi(mail)

    onComplete(result) {
      handleError(s"Error retrieving descriptor $descriptorId of eservice $eserviceId") orElse {
        case Failure(x: EServiceDescriptorNotFound) =>
          logger.warn(x.getMessage)
          getEserviceDescriptor404(problemOf(StatusCodes.NotFound, x))
        case Success(descriptor)                    => getEserviceDescriptor200(descriptor)
      }
    }
  }

  override def getEServicesCatalog(q: Option[String], producersIds: String, states: String, offset: Int, limit: Int)(
    implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCatalogEServices: ToEntityMarshaller[CatalogEServices]
  ): Route = {
    val result = for {
      requesterId       <- getOrganizationIdFutureUUID(contexts)
      apiStates         <- parseArrayParameters(states).traverse(EServiceDescriptorState.fromValue).toFuture
      pagedResults      <- catalogProcessService.getEServices(
        name = q,
        producersIds = parseArrayParameters(producersIds),
        states = apiStates.map(CatalogProcess.EServiceDescriptorState.fromApi),
        offset = offset,
        limit = limit
      )
      enhancedEServices <- Future.traverse(pagedResults.eservices)(enhanceEService(requesterId))
    } yield CatalogEServices(
      eservices = enhancedEServices,
      pagination = Pagination(offset = offset, limit = limit, totalResults = pagedResults.totalCount)
    )

    onComplete(result) {
      handleError(s"Error retrieving Catalog EServices") orElse { case Success(eServices) =>
        getEServicesCatalog200(eServices)
      }
    }
  }

  private def enhanceEService(
    requesterId: UUID
  )(eService: CatalogProcess.EService)(implicit contexts: Seq[(String, String)]): Future[CatalogEService] = for {
    // TODO Use directly the tenant once the name will be added to its model
    producerTenant      <- tenantManagementService.getTenant(eService.producerId)
    selfcareId          <- producerTenant.selfcareId.toFuture(MissingSelfcareId(producerTenant.id))
    producerInstitution <- partyProcessService.getInstitution(selfcareId)
    // End TODO

    requesterTenant <-
      if (requesterId != eService.producerId) tenantManagementService.getTenant(requesterId)
      else Future.successful(producerTenant)

    activeDescriptor = eService.descriptors
      .filter(d => ACTIVE_DESCRIPTOR_STATES_FILTER.contains(d.state))
      .sortBy(_.version.toInt)
      .lastOption

    agreement <- activeDescriptor.flatTraverse(d =>
      agreementProcessService
        .getAgreements(
          consumerId = requesterId.toString.some,
          eServiceId = eService.id.toString.some,
          descriptorId = d.id.toString.some,
          states = Nil
        )
        .map(_.headOption)
    )
  } yield CatalogEService(
    id = eService.id,
    name = eService.name,
    description = eService.description,
    producer = CompactOrganization(id = eService.producerId, name = producerInstitution.description),
    agreement = agreement.map(a => CompactAgreement(id = a.id, state = a.state.toApi)),
    isMine = eService.producerId == requesterId,
    canSubscribe =
      certifiedAttributesSatisfied(eService.attributes.toManagement, requesterTenant.attributes.mapFilter(_.certified)),
    activeDescriptor = activeDescriptor.map(d => CompactDescriptor(id = d.id, state = d.state.toApi, d.version))
  )

}
