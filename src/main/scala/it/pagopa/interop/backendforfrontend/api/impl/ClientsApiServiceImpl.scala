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
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service.types.AuthorizationProcessServiceTypes._

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

  override def removeClientPurpose(clientId: String, purposeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    val result: Future[Unit] = for {
      clientUuid  <- clientId.toFutureUUID
      purposeUuid <- purposeId.toFutureUUID
      _           <- authorizationProcessService.removeClientPurpose(clientUuid, purposeUuid)
    } yield ()

    onComplete(result) {
      handleError(s"Error removing purpose $purposeId for client $clientId") orElse { case Success(_) =>
        removeClientPurpose204
      }
    }
  }

  override def deleteClientKeyById(clientId: String, keyId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    val result: Future[Unit] = for {
      clientUuid <- clientId.toFutureUUID
      _          <- authorizationProcessService.deleteClientKeyById(clientUuid, keyId)
    } yield ()

    onComplete(result) {
      handleError(s"Error deleting key $keyId of client $clientId") orElse { case Success(_) =>
        deleteClientKeyById204
      }
    }
  }

  override def removeClientOperatorRelationship(clientId: String, relationshipId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    val result: Future[Unit] = for {
      clientUuid       <- clientId.toFutureUUID
      relationshipUuid <- relationshipId.toFutureUUID
      _                <- authorizationProcessService.removeClientOperatorRelationship(clientUuid, relationshipUuid)
    } yield ()

    onComplete(result) {
      handleError(s"Error removing operator relationship $relationshipId of client $clientId") orElse {
        case Success(_) =>
          removeClientOperatorRelationship204
      }
    }
  }

  override def clientOperatorRelationshipBinding(clientId: String, relationshipId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {

    val result: Future[CreatedResource] = for {
      clientUuid       <- clientId.toFutureUUID
      relationshipUuid <- relationshipId.toFutureUUID
      result           <- authorizationProcessService.clientOperatorRelationshipBinding(clientUuid, relationshipUuid)
    } yield (result.toCreatedResource)

    onComplete(result) {
      handleError(s"Error binding operator relationship $relationshipId to client $clientId") orElse {
        case Success(resource) =>
          clientOperatorRelationshipBinding200(resource)
      }
    }
  }

  override def addClientPurpose(clientId: String, purposeAdditionDetailsSeed: PurposeAdditionDetailsSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    val result: Future[Unit] = for {
      clientUuid <- clientId.toFutureUUID
      _          <- authorizationProcessService.addClientPurpose(clientUuid, purposeAdditionDetailsSeed.toProcess)
    } yield ()

    onComplete(result) {
      handleError(s"Error adding purpose ${purposeAdditionDetailsSeed.purposeId} to client $clientId") orElse {
        case Success(_) =>
          addClientPurpose204
      }
    }
  }

  override def createKeys(clientId: String, keySeed: Seq[KeySeed])(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    val result: Future[Unit] = for {
      clientUuid <- clientId.toFutureUUID
      _          <- authorizationProcessService.createKeys(clientUuid, keySeed.map(_.toProcess))
    } yield ()

    onComplete(result) {
      handleError(s"Error creating keys to client $clientId") orElse { case Success(_) =>
        createKeys204(_)
      }
    }
  }
}
