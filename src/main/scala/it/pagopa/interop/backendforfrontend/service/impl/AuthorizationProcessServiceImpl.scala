package it.pagopa.interop.backendforfrontend.service.impl

import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.AuthorizationProcessService
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders
import it.pagopa.interop.authorizationprocess.client.api.{EnumsSerializers, ClientApi}
import it.pagopa.interop.authorizationprocess.client.invoker.{ApiInvoker, ApiRequest, BearerToken}
import it.pagopa.interop.authorizationprocess.client.model.Client

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
      invoker.invoke(request, s"Binding operator relationship $relationshipId of client ${clientId.toString}")
    }
}
