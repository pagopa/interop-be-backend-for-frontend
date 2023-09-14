package it.pagopa.interop.backendforfrontend.api.impl

import it.pagopa.interop.commons.utils.TypeConversions._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.SelfcareApiService
import it.pagopa.interop.backendforfrontend.service.{SelfcareClientService, TenantProcessService}
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.commons.utils.AkkaUtils._
import it.pagopa.interop.backendforfrontend.error.BFFErrors._
import it.pagopa.interop.backendforfrontend.service.types.SelfcareClientTypes._
import it.pagopa.interop.backendforfrontend.common.HeaderUtils._
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.HttpHeader

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class SelfcareApiServiceImpl(
  selfcareClientService: SelfcareClientService,
  tenantProcessService: TenantProcessService
)(implicit ec: ExecutionContext)
    extends SelfcareApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getInstitutionUserProducts()(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerSelfcareProductarray: ToEntityMarshaller[Seq[SelfcareProduct]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Seq[SelfcareProduct]] =
      for {
        userUuid         <- getUidFutureUUID(contexts)
        organizationUuId <- getOrganizationIdFutureUUID(contexts)
        _ = logger.info(
          s"Retrieving products for institution ${organizationUuId.toString} of user ${userUuid.toString}"
        )
        tenant     <- tenantProcessService.getTenant(organizationUuId)
        selfcareId <- tenant.selfcareId.toFuture(MissingSelfcareId(tenant.id))
        results    <- selfcareClientService.getInstitutionUserProducts(userId = userUuid, institutionId = selfcareId)
        apiResults = results.map(_.toApi)
      } yield apiResults

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving products for institution", headers) orElse { case Success(resources) =>
        getInstitutionUserProducts200(headers)(resources)
      }
    }
  }

  override def getInstitutions()(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerSelfcareInstitutionarray: ToEntityMarshaller[Seq[SelfcareInstitution]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Seq[SelfcareInstitution]] =
      for {
        userUuid <- getUidFutureUUID(contexts)
        results  <- selfcareClientService.getInstitutions(userId = userUuid)
        apiResults = results.map(_.toApi)
      } yield apiResults

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving institutions", headers) orElse { case Success(resources) =>
        getInstitutions200(headers)(resources)
      }
    }
  }
}
