package it.pagopa.interop.backendforfrontend.api.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.SelfcareApiService
import it.pagopa.interop.backendforfrontend.service.{SelfcareClientService}
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.backendforfrontend.service.types.SelfcareClientTypes._

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class SelfcareApiServiceImpl(selfcareClientService: SelfcareClientService)(implicit ec: ExecutionContext)
    extends SelfcareApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getInstitutionUserProducts(userId: String, institutionId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerCompactProductarray: ToEntityMarshaller[Seq[CompactProduct]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Seq[CompactProduct]] =
      for {
        userUuid <- userId.toFutureUUID
        results  <- selfcareClientService.getInstitutionUserProducts(userId = userUuid, institutionId = institutionId)
        apiResults = results.map(_.toApi)
      } yield apiResults

    onComplete(result) {
      handleError(s"Error retrieving products for institution $institutionId of user $userId") orElse {
        case Success(resources) =>
          getInstitutionUserProducts200(resources)
      }
    }
  }
}
