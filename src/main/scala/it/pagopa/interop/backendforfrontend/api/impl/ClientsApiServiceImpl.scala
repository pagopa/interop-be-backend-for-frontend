package it.pagopa.interop.backendforfrontend.api.impl

import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.backendforfrontend.service.{
  AuthorizationProcessService,
  CatalogProcessService,
  PartyProcessService,
  PurposeProcessService,
  TenantProcessService,
  UserRegistryService
}
import it.pagopa.interop.backendforfrontend.api.ClientsApiService
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.backendforfrontend.model.{RelationshipState, _}
import it.pagopa.interop.commons.utils.AkkaUtils._
import it.pagopa.interop.backendforfrontend.service.types.AuthorizationProcessServiceTypes._
import it.pagopa.interop.authorizationprocess.client.{model => AuthorizationProcess}
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.authorizationprocess.client.invoker.{ApiError => PartyProcessApiError}
import it.pagopa.interop.authorizationprocess.client.{model => AuthorizationProcessModel}
import it.pagopa.interop.backendforfrontend.api.impl.converters.PartyProcessConverter
import it.pagopa.interop.selfcare.userregistry.client.model.UserResource

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class ClientsApiServiceImpl(
  authorizationProcessService: AuthorizationProcessService,
  tenantProcessService: TenantProcessService,
  catalogProcessService: CatalogProcessService,
  purposeProcessService: PurposeProcessService,
  partyProcessService: PartyProcessService,
  userRegistryService: UserRegistryService
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

  override def getClientKeyById(clientId: String, keyId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPublicKey: ToEntityMarshaller[PublicKey],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    val result: Future[PublicKey] = for {
      clientUuid    <- clientId.toFutureUUID
      readClientKey <- authorizationProcessService.getClientKeyById(clientUuid, keyId)
      user          <- userRegistryService.findById(readClientKey.relationshipId)
      key           <- decorateKey(readClientKey, user)
    } yield key

    onComplete(result) {
      handleError(s"Error retrieving key $keyId of client $clientId") orElse { case Success(key) =>
        getClientKeyById200(key)
      }
    }
  }

  override def getClientOperators(clientId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerOperatorarray: ToEntityMarshaller[Seq[Operator]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    val result: Future[Seq[Operator]] = for {
      clientUuid <- clientId.toFutureUUID
      operators  <- authorizationProcessService.getClientOperators(clientUuid)
    } yield (operators.map(_.toApi))

    onComplete(result) {
      handleError(s"Error retrieving operators for client $clientId") orElse { case Success(operators) =>
        getClientOperators200(operators)
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
      handleError(s"Error creating keys for client $clientId") orElse { case Success(_) => createKeys204(_) }
    }
  }

  override def getEncodedClientKeyById(clientId: String, keyId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEncodedClientKey: ToEntityMarshaller[EncodedClientKey],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    val result: Future[EncodedClientKey] = for {
      clientUuid       <- clientId.toFutureUUID
      encodedClientKey <- authorizationProcessService.getClientKeyById(clientUuid, keyId)
    } yield EncodedClientKey(encodedClientKey.encodedPem)

    onComplete(result) {
      handleError(s"Error retrieving key $keyId for client ${clientId}") orElse { case Success(key) =>
        getEncodedClientKeyById200(key)
      }
    }
  }

  override def createConsumerClient(clientSeed: ClientSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClient: ToEntityMarshaller[CreatedResource]
  ): Route = {

    val result: Future[CreatedResource] =
      authorizationProcessService.createConsumerClient(clientSeed.toProcess) map (_.toCreatedResource)

    onComplete(result) {
      handleError(s"Error creating consumer client with name ${clientSeed.name}") orElse { case Success(resource) =>
        createConsumerClient200(resource)
      }
    }
  }

  override def createApiClient(clientSeed: ClientSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {

    val result: Future[CreatedResource] =
      authorizationProcessService.createApiClient(clientSeed.toProcess) map (_.toCreatedResource)

    onComplete(result) {
      handleError(s"Error creating api client with name ${clientSeed.name}") orElse { case Success(resource) =>
        createApiClient200(resource)
      }
    }
  }

  override def getClients(q: Option[String], relationshipIds: String, kind: Option[String], offset: Int, limit: Int)(
    implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCompactClients: ToEntityMarshaller[CompactClients]
  ): Route = {
    val result: Future[CompactClients] = for {
      requesterUuid     <- getOrganizationIdFutureUUID(contexts)
      relationshipsUuid <- parseArrayParameters(relationshipIds).traverse(_.toFutureUUID)
      clientKind        <- kind.traverse(ClientKind.fromValue).toFuture
      pagedResults      <- authorizationProcessService.getClientsWithKeys(
        name = q,
        relationshipIds = relationshipsUuid,
        consumerId = requesterUuid,
        purposeId = None,
        kind = clientKind.map(_.toProcess),
        offset = offset,
        limit = limit
      )
    } yield CompactClients(
      results = pagedResults.results.map(_.toApi),
      pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
    )

    onComplete(result) {
      handleError(s"Error retrieving clients") orElse { case Success(clients) => getClients200(clients) }
    }
  }

  override def getClientKeys(clientId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerPublicKeys: ToEntityMarshaller[PublicKeys]
  ): Route = {

    val result: Future[PublicKeys] = for {
      clientUuid     <- clientId.toFutureUUID
      readClientKeys <- authorizationProcessService.getClientKeys(clientUuid, Seq.empty)
      keys           <- Future.traverse(readClientKeys.keys)(key =>
        for {
          user         <- userRegistryService.findById(key.relationshipId)
          decoratedKey <- decorateKey(key, user)
        } yield decoratedKey
      )
    } yield PublicKeys(keys)

    onComplete(result) {
      handleError(s"Error retrieving keys of client $clientId") orElse { case Success(keys) =>
        getClientKeys200(keys)
      }
    }
  }

  private def decorateKey(key: AuthorizationProcessModel.Key, user: UserResource)(implicit
    contexts: Seq[(String, String)]
  ): Future[PublicKey] = {

    val result: Future[PublicKey] = for {
      relationship     <- partyProcessService.getRelationship(key.relationshipId)
      relationshipInfo <- PartyProcessConverter.toApiRelationshipInfo(user, relationship)
    } yield relationshipInfo.state match {
      case RelationshipState.ACTIVE => key.toApi(isOrphan = false, user)
      case _                        => key.toApi(isOrphan = true, user)
    }

    result.recoverWith {
      case PartyProcessApiError(404, _, _, _, _) => Future.successful(key.toApi(isOrphan = true, user))
      case other                                 => Future.failed(other)
    }
  }

  override def getClient(clientId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClient: ToEntityMarshaller[Client]
  ): Route = {

    val result: Future[Client] = for {
      clientUuid <- clientId.toFutureUUID
      client     <- authorizationProcessService.getClient(clientUuid)
      apiClient  <- enhanceClient(client)
    } yield apiClient

    onComplete(result) {
      handleError(s"Error retrieving client $clientId") orElse { case Success(client) =>
        getClient200(client)
      }
    }
  }

  private def enhanceClient(
    client: AuthorizationProcess.Client
  )(implicit contexts: Seq[(String, String)]): Future[Client] = for {
    consumer <- tenantProcessService
      .getTenant(client.consumerId)
    purposes <- Future.traverse(client.purposes)(enhancePurpose)
  } yield Client(
    id = client.id,
    consumer = CompactOrganization(consumer.id, consumer.name),
    name = client.name,
    purposes = purposes,
    description = client.description,
    kind = client.kind.toApi,
    createdAt = client.createdAt
  )

  private def enhancePurpose(
    clientPurpose: AuthorizationProcess.ClientPurpose
  )(implicit contexts: Seq[(String, String)]): Future[ClientPurpose] = for {
    (eService, purpose) <- catalogProcessService
      .getEServiceById(clientPurpose.states.eservice.eserviceId)
      .zip(purposeProcessService.getPurpose(clientPurpose.states.purpose.purposeId))
    producer            <- tenantProcessService
      .getTenant(eService.producerId)
  } yield ClientPurpose(
    purposeId = purpose.id,
    title = purpose.title,
    eservice = CompactEService(id = eService.id, name = eService.name, CompactOrganization(producer.id, producer.name))
  )

  override def getClientRelationshipKeys(clientId: String, relationshipId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerPublicKeys: ToEntityMarshaller[PublicKeys]
  ): Route = {

    val result: Future[PublicKeys] = for {
      clientUuid       <- clientId.toFutureUUID
      relationshipUuid <- relationshipId.toFutureUUID
      readClientKeys   <- authorizationProcessService.getClientKeys(clientUuid, Seq(relationshipUuid))
      keys             <- Future.traverse(readClientKeys.keys)(key =>
        for {
          user         <- userRegistryService.findById(key.relationshipId)
          decoratedKey <- decorateKey(key, user)
        } yield decoratedKey
      )
    } yield PublicKeys(keys)

    onComplete(result) {
      handleError(s"Error retrieving keys to client $clientId and relationship $relationshipId") orElse {
        case Success(keys) =>
          getClientRelationshipKeys200(keys)
      }
    }
  }
}
