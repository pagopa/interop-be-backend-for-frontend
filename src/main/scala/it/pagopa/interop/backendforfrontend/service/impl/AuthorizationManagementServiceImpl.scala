package it.pagopa.interop.backendforfrontend.service.impl

import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.authorizationmanagement.client.api.{EnumsSerializers, TokenGenerationApi}
import it.pagopa.interop.authorizationmanagement.client.invoker.{ApiError, ApiInvoker, ApiRequest}
import it.pagopa.interop.authorizationmanagement.client.model.KeyWithClient
import it.pagopa.interop.backendforfrontend.error.BFFErrors.KidNotFound
import it.pagopa.interop.backendforfrontend.service.AuthorizationManagementService
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class AuthorizationManagementServiceImpl(authorizationManagementUrl: String, blockingEc: ExecutionContextExecutor)(
  implicit system: ActorSystem[_]
) extends AuthorizationManagementService {

  val invoker: ApiInvoker           = ApiInvoker(EnumsSerializers.all, blockingEc)(system.classicSystem)
  val api: TokenGenerationApi       = TokenGenerationApi(authorizationManagementUrl)
  implicit val ec: ExecutionContext = blockingEc

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  def getKeyWithClient(clientId: UUID, kid: String)(implicit contexts: Seq[(String, String)]): Future[KeyWithClient] =
    withHeaders[KeyWithClient] { (_, correlationId, ip) =>
      val request: ApiRequest[KeyWithClient] =
        api.getKeyWithClientByKeyId(
          clientId = clientId,
          keyId = kid,
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )
      invoker
        .invoke(request, s"Retrieve key $kid of client ${clientId.toString}")
        .recoverWith { case err: ApiError[_] if err.code == 404 => Future.failed(KidNotFound(clientId, kid)) }
    }

}
