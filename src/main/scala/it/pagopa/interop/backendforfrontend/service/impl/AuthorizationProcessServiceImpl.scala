package it.pagopa.interop.backendforfrontend.service.impl

import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.authorizationprocess.client.api.{ClientApi, EnumsSerializers, OperatorApi}
import it.pagopa.interop.authorizationprocess.client.invoker.{ApiInvoker, ApiRequest, BearerToken}
import it.pagopa.interop.authorizationprocess.client.model._
import it.pagopa.interop.backendforfrontend.service.AuthorizationProcessService
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}

class AuthorizationProcessServiceImpl(authorizationProcessUrl: String, blockingEc: ExecutionContextExecutor)(implicit
  system: ActorSystem[_]
) extends AuthorizationProcessService {

  val invoker: ApiInvoker      = ApiInvoker(EnumsSerializers.all, blockingEc)(system.classicSystem)
  val clientApi: ClientApi     = ClientApi(authorizationProcessUrl)
  val operatorApi: OperatorApi = OperatorApi(authorizationProcessUrl)

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def deleteClient(clientId: String)(implicit contexts: Seq[(String, String)]): Future[Unit] =
    withHeaders[Unit] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Unit] =
        clientApi.deleteClient(clientId = clientId, xCorrelationId = correlationId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Deleting client $clientId")
    }

  override def removeClientPurpose(clientId: UUID, purposeId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] =
    withHeaders[Unit] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Unit] =
        clientApi.removeClientPurpose(
          clientId = clientId,
          purposeId = purposeId,
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Removing purpose ${purposeId.toString} for client ${clientId.toString}")
    }

  override def deleteClientKeyById(clientId: UUID, keyId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] =
    withHeaders[Unit] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Unit] =
        clientApi.deleteClientKeyById(
          clientId = clientId,
          keyId = keyId,
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Deleting key $keyId of client ${clientId.toString}")
    }

  override def removeClientOperatorRelationship(clientId: UUID, relationshipId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] =
    withHeaders[Unit] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Unit] =
        clientApi.removeClientOperatorRelationship(
          clientId = clientId,
          relationshipId = relationshipId,
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Removing operator relationship $relationshipId of client ${clientId.toString}")
    }

  override def addClientPurpose(clientId: UUID, purposeAdditionDetails: PurposeAdditionDetails)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] =
    withHeaders[Unit] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Unit] =
        clientApi.addClientPurpose(
          clientId = clientId,
          purposeAdditionDetails = purposeAdditionDetails,
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Adding purpose ${purposeAdditionDetails.purposeId} to client ${clientId.toString}")
    }

  override def getClientKeys(clientId: UUID)(implicit contexts: Seq[(String, String)]): Future[ReadClientKeys] =
    withHeaders[ReadClientKeys] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[ReadClientKeys] =
        clientApi.getClientKeys(clientId = clientId, xCorrelationId = correlationId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Retrieve keys of client ${clientId.toString}")
    }

  override def clientOperatorRelationshipBinding(clientId: UUID, relationshipId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Client] =
    withHeaders[Client] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Client] =
        clientApi.clientOperatorRelationshipBinding(
          clientId = clientId,
          relationshipId = relationshipId,
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Binding operator relationship $relationshipId to client ${clientId.toString}")
    }

  override def getClientKeyById(clientId: UUID, keyId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[ReadClientKey] =
    withHeaders[ReadClientKey] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[ReadClientKey] =
        clientApi.getClientKeyById(
          clientId = clientId,
          keyId = keyId,
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Retrieve key ${keyId} of client ${clientId.toString}")
    }

  override def getClientOperators(clientId: UUID)(implicit contexts: Seq[(String, String)]): Future[Seq[Operator]] =
    withHeaders[Seq[Operator]] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Seq[Operator]] =
        clientApi.getClientOperators(clientId = clientId, xCorrelationId = correlationId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Retrieving operators for client ${clientId.toString}")
    }

  override def createKeys(clientId: UUID, keySeed: Seq[KeySeed])(implicit
    contexts: Seq[(String, String)]
  ): Future[ClientKeys] =
    withHeaders[ClientKeys] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[ClientKeys] =
        clientApi.createKeys(
          clientId = clientId,
          keySeed = keySeed,
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Create keys for client ${clientId.toString}")
    }

  override def getEncodedClientKeyById(clientId: UUID, keyId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[EncodedClientKey] =
    withHeaders[EncodedClientKey] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[EncodedClientKey] =
        clientApi.getEncodedClientKeyById(
          clientId = clientId,
          keyId = keyId,
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Retrieving encoding key $keyId for client ${clientId.toString}")
    }

  override def createConsumerClient(clientSeed: ClientSeed)(implicit contexts: Seq[(String, String)]): Future[Client] =
    withHeaders[Client] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Client] =
        clientApi.createConsumerClient(clientSeed = clientSeed, xCorrelationId = correlationId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Creating consumer client with name ${clientSeed.name}")
    }

  override def createApiClient(clientSeed: ClientSeed)(implicit contexts: Seq[(String, String)]): Future[Client] =
    withHeaders[Client] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Client] =
        clientApi.createApiClient(clientSeed = clientSeed, xCorrelationId = correlationId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Create api client with name ${clientSeed.name}")
    }

  override def getClients(
    name: Option[String],
    relationshipIds: Seq[UUID],
    consumerId: UUID,
    purposeId: Option[UUID],
    kind: Option[ClientKind],
    limit: Int,
    offset: Int
  )(implicit contexts: Seq[(String, String)]): Future[Clients] = withHeaders[Clients] {
    (bearerToken, correlationId, ip) =>
      val request = clientApi.getClients(
        xCorrelationId = correlationId,
        name = name,
        relationshipIds = relationshipIds,
        consumerId = consumerId,
        purposeId = purposeId,
        kind = kind,
        offset = offset,
        limit = limit,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      invoker.invoke(request, s"Retrieving clients")
  }

  override def getClient(clientId: UUID)(implicit contexts: Seq[(String, String)]): Future[Client] =
    withHeaders[Client] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Client] =
        clientApi.getClient(clientId = clientId.toString, xCorrelationId = correlationId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Retrieve client $clientId")
    }

  override def getClientOperatorKeys(clientId: UUID, operatorId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[ClientKeys] =
    withHeaders[ClientKeys] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[ClientKeys] =
        operatorApi.getClientOperatorKeys(
          clientId = clientId,
          operatorId = operatorId,
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Retrieving keys for client ${clientId.toString} and operator ${operatorId.toString}")
    }
}
