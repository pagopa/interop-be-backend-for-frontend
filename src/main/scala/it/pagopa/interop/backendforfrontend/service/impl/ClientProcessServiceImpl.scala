package it.pagopa.interop.backendforfrontend.service.impl

import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders
import it.pagopa.interop.backendforfrontend.service.ClientProcessService
import it.pagopa.interop.authorizationprocess.client.api.{EnumsSerializers, ClientApi}
import it.pagopa.interop.authorizationprocess.client.invoker.{ApiInvoker, ApiRequest, BearerToken}

import scala.concurrent.{ExecutionContextExecutor, Future}

class ClientProcessServiceImpl(authorizationProcessUrl: String, blockingEc: ExecutionContextExecutor)(implicit
  system: ActorSystem[_]
) extends ClientProcessService {

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

}
