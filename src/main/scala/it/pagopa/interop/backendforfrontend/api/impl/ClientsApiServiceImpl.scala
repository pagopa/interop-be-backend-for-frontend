package it.pagopa.interop.backendforfrontend.api.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.backendforfrontend.service.AuthorizationProcessService
import it.pagopa.interop.backendforfrontend.api.ClientsApiService
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.backendforfrontend.model.Problem

import scala.concurrent.{Future, ExecutionContext}
import scala.util.Success

final case class ClientsApiServiceImpl(authorizationProcessService: AuthorizationProcessService)(implicit
  ec: ExecutionContext
) extends ClientsApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def deleteClient(
    clientId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    val result: Future[Unit] = authorizationProcessService.deleteClient(clientId)

    onComplete(result) {
      handleError(s"Error deleting client $clientId") orElse { case Success(_) => deleteClient204 }
    }
  }

  override def deleteClientPurpose(clientId: String, purposeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    val result: Future[Unit] = for {
      clientUuid  <- clientId.toFutureUUID
      purposeUuid <- purposeId.toFutureUUID
      _           <- authorizationProcessService.deleteClientPurpose(clientUuid, purposeUuid)
    } yield ()

    onComplete(result) {
      handleError(s"Error deleting purpose $purposeId for client $clientId") orElse { case Success(_) =>
        deleteClientPurpose204
      }
    }
  }
}
