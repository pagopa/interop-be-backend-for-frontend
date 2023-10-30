package it.pagopa.interop.backendforfrontend.api.impl

import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.backendforfrontend.service.{
  AuthorizationProcessService,
  CatalogProcessService,
  PurposeProcessService,
  TenantProcessService,
  SelfcareV2ClientService
}
import it.pagopa.interop.backendforfrontend.service.types.SelfcareV2ClientServiceTypes._
import it.pagopa.interop.backendforfrontend.api.ClientsApiService
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.HttpHeader
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.backendforfrontend.common.HeaderUtils._
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.commons.utils.AkkaUtils._
import it.pagopa.interop.backendforfrontend.service.types.TenantProcessServiceTypes._
import it.pagopa.interop.backendforfrontend.service.types.AuthorizationProcessServiceTypes._
import it.pagopa.interop.authorizationprocess.client.{model => AuthorizationProcess}
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.authorizationprocess.client.{model => AuthorizationProcessModel}
import it.pagopa.interop.backendforfrontend.error.BFFErrors.UserNotFound
import it.pagopa.interop.selfcare.v2.client.model.UserResponse

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class ClientsApiServiceImpl(
  authorizationProcessService: AuthorizationProcessService,
  tenantProcessService: TenantProcessService,
  catalogProcessService: CatalogProcessService,
  purposeProcessService: PurposeProcessService,
  selfcareV2ClientService: SelfcareV2ClientService
)(implicit ec: ExecutionContext)
    extends ClientsApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def deleteClient(
    clientId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    val result: Future[Unit] = authorizationProcessService.deleteClient(clientId)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error deleting client $clientId", headers) orElse { case Success(_) =>
        deleteClient204(headers)
      }
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
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error removing purpose $purposeId for client $clientId", headers) orElse { case Success(_) =>
        removeClientPurpose204(headers)
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
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error deleting key $keyId of client $clientId", headers) orElse { case Success(_) =>
        deleteClientKeyById204(headers)
      }
    }
  }

  override def removeUserFromClient(clientId: String, userId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    val result: Future[Unit] = for {
      clientUuid <- clientId.toFutureUUID
      userUuid   <- userId.toFutureUUID
      _          <- authorizationProcessService.removeUser(clientUuid, userUuid)
    } yield ()

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error removing user $userId of client $clientId", headers) orElse { case Success(_) =>
        removeUserFromClient204(headers)
      }
    }
  }

  override def addUserToClient(clientId: String, userId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource]
  ): Route = {

    val result: Future[CreatedResource] = for {
      clientUuid <- clientId.toFutureUUID
      userUuid   <- userId.toFutureUUID
      result     <- authorizationProcessService.addUser(clientUuid, userUuid)
    } yield (result.toCreatedResource)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error add user $userId to client $clientId", headers) orElse { case Success(resource) =>
        addUserToClient200(headers)(resource)
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
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error adding purpose ${purposeAdditionDetailsSeed.purposeId} to client $clientId", headers) orElse {
        case Success(_) =>
          addClientPurpose204(headers)
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
      key           <- decorateKey(readClientKey)
    } yield key

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving key $keyId of client $clientId", headers) orElse { case Success(key) =>
        getClientKeyById200(headers)(key)
      }
    }
  }

  override def getClientUsers(clientId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerUUIDarray: ToEntityMarshaller[Seq[SelfcareUser]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    val result: Future[Seq[SelfcareUser]] = for {
      clientUuid   <- clientId.toFutureUUID
      selfcareUuid <- getSelfcareIdFutureUUID(contexts)
      clientUsers  <- authorizationProcessService.getClientUsers(clientUuid)
      users        <- Future.traverse(clientUsers)(selfcareV2ClientService.getUserById(selfcareUuid, _))
      usersApi     <- Future.traverse(users)(_.toApi.toFuture)
    } yield usersApi

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving users for client $clientId", headers) orElse { case Success(users) =>
        getClientUsers200(headers)(users)
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
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error creating keys for client $clientId", headers) orElse { case Success(_) =>
        createKeys204(headers)(_)
      }
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
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving key $keyId for client $clientId", headers) orElse { case Success(key) =>
        getEncodedClientKeyById200(headers)(key)
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
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error creating consumer client with name ${clientSeed.name}", headers) orElse {
        case Success(resource) =>
          createConsumerClient200(headers)(resource)
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
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error creating api client with name ${clientSeed.name}", headers) orElse { case Success(resource) =>
        createApiClient200(headers)(resource)
      }
    }
  }

  override def getClients(q: Option[String], userIds: String, kind: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCompactClients: ToEntityMarshaller[CompactClients]
  ): Route = {
    val result: Future[CompactClients] = for {
      requesterUuid <- getOrganizationIdFutureUUID(contexts)
      usersUuid     <- parseArrayParameters(userIds).traverse(_.toFutureUUID)
      clientKind    <- kind.traverse(ClientKind.fromValue).toFuture
      pagedResults  <- authorizationProcessService.getClientsWithKeys(
        name = q,
        userIds = usersUuid,
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
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving clients", headers) orElse { case Success(clients) =>
        getClients200(headers)(clients)
      }
    }
  }

  override def getClientKeys(userIds: String, clientId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerPublicKeys: ToEntityMarshaller[PublicKeys]
  ): Route = {

    val result: Future[PublicKeys] = for {
      clientUuid     <- clientId.toFutureUUID
      usersUuid      <- parseArrayParameters(userIds).traverse(_.toFutureUUID)
      readClientKeys <- authorizationProcessService.getClientKeys(clientUuid, usersUuid)
      keys           <- Future.traverse(readClientKeys.keys)(decorateKey)
    } yield PublicKeys(keys)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving keys of client $clientId", headers) orElse { case Success(keys) =>
        getClientKeys200(headers)(keys)
      }
    }
  }

  private def decorateKey(
    key: AuthorizationProcessModel.Key
  )(implicit contexts: Seq[(String, String)]): Future[PublicKey] = {
    for {
      selfcareUuid <- getSelfcareIdFutureUUID(contexts)
      userResponse <- selfcareV2ClientService.getUserById(selfcareUuid, key.userId).recoverWith{case _: UserNotFound => Future.successful(UserResponse())}
      (user, isOrphan)         <- userResponse match {
        case UserResponse(_, id, _, _, _) if id == None => userResponse.toApi.toFuture.zip(Future.successful(true))
        case _ => userResponse.toApi.toFuture.zip(Future.successful(false))
      }
    } yield key.toApi(user = user, isOrphan = isOrphan)
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
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving client $clientId", headers) orElse { case Success(client) =>
        getClient200(headers)(client)
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
    eservice = CompactEService(
      id = eService.id,
      name = eService.name,
      CompactOrganization(producer.id, producer.name, producer.kind.map(_.toApi))
    )
  )

  override def getClientUserKeys(clientId: String, userId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerPublicKeys: ToEntityMarshaller[PublicKeys]
  ): Route = {

    val result: Future[PublicKeys] = for {
      clientUuid     <- clientId.toFutureUUID
      userUuid       <- userId.toFutureUUID
      readClientKeys <- authorizationProcessService.getClientKeys(clientUuid, Seq(userUuid))
      keys           <- Future.traverse(readClientKeys.keys)(decorateKey)
    } yield PublicKeys(keys)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving keys to client $clientId and user $userId", headers) orElse { case Success(keys) =>
        getClientUserKeys200(headers)(keys)
      }
    }
  }
}
