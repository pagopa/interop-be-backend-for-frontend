package it.pagopa.interop.backendforfrontend.service.impl

import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.authorizationprocess.client.api.{ClientApi, EnumsSerializers}
import it.pagopa.interop.authorizationprocess.client.invoker.{ApiInvoker, ApiRequest, BearerToken}
import it.pagopa.interop.authorizationprocess.client.model.{Clients, ReadClientKeys}
import it.pagopa.interop.backendforfrontend.service.AuthorizationProcessService
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders
import it.pagopa.interop.authorizationprocess.client.api.{EnumsSerializers, ClientApi}
import it.pagopa.interop.authorizationprocess.client.invoker.{ApiInvoker, ApiRequest, BearerToken}
import it.pagopa.interop.authorizationprocess.client.model._

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}

class AuthorizationProcessServiceImpl(authorizationProcessUrl: String, blockingEc: ExecutionContextExecutor)(implicit
  system: ActorSystem[_]
) extends AuthorizationProcessService {

  val invoker: ApiInvoker = ApiInvoker(EnumsSerializers.all, blockingEc)(system.classicSystem)
  val api: ClientApi      = ClientApi(authorizationProcessUrl)

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def deleteClient(clientId: String)(implicit contexts: Seq[(String, String)]): Future[Unit] =
    withHeaders[Unit] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Unit] =
        api.deleteClient(clientId = clientId, xCorrelationId = correlationId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Deleting client $clientId")
    }

  override def removeClientPurpose(clientId: UUID, purposeId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] =
    withHeaders[Unit] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Unit] =
        api.removeClientPurpose(
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
        api.deleteClientKeyById(clientId = clientId, keyId = keyId, xCorrelationId = correlationId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Deleting key $keyId of client ${clientId.toString}")
    }

  override def removeClientOperatorRelationship(clientId: UUID, relationshipId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] =
    withHeaders[Unit] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Unit] =
        api.removeClientOperatorRelationship(
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
        api.addClientPurpose(
          clientId = clientId,
          purposeAdditionDetails = purposeAdditionDetails,
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Adding purpose ${purposeAdditionDetails.purposeId} to client ${clientId.toString}")
    }

  override def getClientKeys(clientId: UUID)(implicit contexts: Seq[(String, String)]): Future[ReadClientKeys] =
    withHeaders[ReadClientKeys] { (bearerToken, correlationId, ip) =>
      val request =
        api.getClientKeys(xCorrelationId = correlationId, clientId = clientId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Retrieving keys for client $clientId")
    }

  override def getClients(consumerId: UUID, purposeId: Option[UUID])(implicit
    contexts: Seq[(String, String)]
  ): Future[Clients] = withHeaders[Clients] { (bearerToken, correlationId, ip) =>
    val request =
      api.listClients(
        xCorrelationId = correlationId,
        xForwardedFor = ip,
        purposeId = purposeId,
        consumerId = consumerId
      )(BearerToken(bearerToken))
    invoker.invoke(request, s"Retrieving clients for purpose $purposeId")
  }

  override def clientOperatorRelationshipBinding(clientId: UUID, relationshipId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Client] =
    withHeaders[Client] { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Client] =
        api.clientOperatorRelationshipBinding(
          clientId = clientId,
          relationshipId = relationshipId,
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Binding operator relationship $relationshipId to client ${clientId.toString}")
    }

  def getClients(name: Option[String], relationshipIds: Seq[UUID], limit: Int, offset: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[Clients] = withHeaders[Clients] { (bearerToken, correlationId, ip) =>
    val request = api.getClients(
      xCorrelationId = correlationId,
      name = name,
      relationshipIds = relationshipIds,
      offset = offset,
      limit = limit,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    invoker.invoke(request, s"Retrieving clients")
  }
}
