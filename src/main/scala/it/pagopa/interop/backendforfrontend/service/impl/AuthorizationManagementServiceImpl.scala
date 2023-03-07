package it.pagopa.interop.backendforfrontend.service.impl

import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.authorizationmanagement.client.api.{ClientApi, EnumsSerializers, KeyApi}
import it.pagopa.interop.authorizationmanagement.client.invoker.{ApiInvoker, BearerToken}
import it.pagopa.interop.authorizationmanagement.client.model.{Client, KeysResponse}
import it.pagopa.interop.backendforfrontend.service.AuthorizationManagementService
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}

class AuthorizationManagementServiceImpl(authorizationManagementURL: String, blockingEc: ExecutionContextExecutor)(
  implicit system: ActorSystem[_]
) extends AuthorizationManagementService {

  val invoker: ApiInvoker = ApiInvoker(EnumsSerializers.all, blockingEc)(system.classicSystem)
  val api: ClientApi      = ClientApi(authorizationManagementURL)
  val keyApi: KeyApi      = KeyApi(authorizationManagementURL)

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getClients(purposeId: Option[UUID])(implicit contexts: Seq[(String, String)]): Future[Seq[Client]] =
    withHeaders[Seq[Client]] { (bearerToken, correlationId, ip) =>
      val request =
        api.listClients(xCorrelationId = correlationId, xForwardedFor = ip, purposeId = purposeId)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Retrieving clients for purpose $purposeId")
    }

  override def getClientKeys(clientId: UUID)(implicit contexts: Seq[(String, String)]): Future[KeysResponse] =
    withHeaders[KeysResponse] { (bearerToken, correlationId, ip) =>
      val request =
        keyApi.getClientKeys(xCorrelationId = correlationId, clientId = clientId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Retrieving keys for client $clientId")
    }
}
