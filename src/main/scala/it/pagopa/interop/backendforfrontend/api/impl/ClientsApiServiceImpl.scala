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
import it.pagopa.interop.authorizationprocess.client.{model => AuthorizationProcessModel}
import it.pagopa.interop.selfcare.partyprocess.client.invoker.{ApiError => PartyProcessApiError}
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service.PartyProcessService
import it.pagopa.interop.backendforfrontend.service.types.AuthorizationProcessServiceTypes._

import scala.concurrent.{Future, ExecutionContext}
import scala.util.Success
import it.pagopa.interop.selfcare.partyprocess.client.model.RelationshipState.ACTIVE

final case class ClientsApiServiceImpl(
  authorizationProcessService: AuthorizationProcessService,
  partyProcessService: PartyProcessService
)(implicit ec: ExecutionContext)
    extends ClientsApiService {

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

  override def getClientKeys(clientId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerReadClientKeys: ToEntityMarshaller[ReadClientKeys],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    def getRelationship(key: AuthorizationProcessModel.ReadClientKey): Future[ReadClientKey] =
      partyProcessService
        .getRelationship(key.operator.relationshipId)
        .map(relationship => {
          relationship.state match {
            case ACTIVE => key.toApi
            case _      => key.toApi.copy(isOrphan = true)
          }
        })
        .recoverWith(apiError =>
          apiError match {
            case PartyProcessApiError(404, _, _, _, _) => Future.successful(key.toApi.copy(isOrphan = true))
            case other                                 => Future.failed(other)
          }
        )

    val result: Future[ReadClientKeys] = for {
      clientUuid     <- clientId.toFutureUUID
      readClientKeys <- authorizationProcessService.getClientKeys(clientUuid)
      keys           <- Future.traverse(readClientKeys.keys)(getRelationship)
    } yield ReadClientKeys(keys)

    onComplete(result) {
      handleError(s"Error retrieving keys of client $clientId") orElse { case Success(keys) =>
        getClientKeys200(keys)
      }
    }
  }

}
