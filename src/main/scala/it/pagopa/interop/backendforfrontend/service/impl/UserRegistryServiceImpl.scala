package it.pagopa.interop.backendforfrontend.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.UserRegistryService
import it.pagopa.interop.selfcare.userregistry.client.invoker.{ApiInvoker, ApiError, ApiKeyValue}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.{
  GenericClientError,
  ResourceNotFoundError,
  ThirdPartyCallError
}
import it.pagopa.interop.selfcare.userregistry.client.api.{UserApi, EnumsSerializers}
import it.pagopa.interop.selfcare.userregistry.client.model.{UserResource, Field}

import java.util.UUID
import scala.concurrent.Future
import akka.actor.typed.ActorSystem

class UserRegistryServiceImpl(userRegistryURL: String, userRegistryApiKey: String)(implicit system: ActorSystem[_])
    extends UserRegistryService {

  implicit val userRegistryApiKeyValue: ApiKeyValue = ApiKeyValue(userRegistryApiKey)
  val invoker: ApiInvoker                           = ApiInvoker(EnumsSerializers.all)(system.classicSystem)
  val api: UserApi                                  = UserApi(userRegistryURL)

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private val serviceName: String = "user-registry"

  def findById(userId: UUID)(implicit contexts: Seq[(String, String)]): Future[UserResource] = {
    val request = api.findByIdUsingGET(userId, Seq(Field.name, Field.familyName, Field.fiscalCode))
    invoker.invoke(request, s"Retrieving user ${userId.toString}", invocationRecovery(userId.toString))
  }

  private def invocationRecovery[T](
    userId: String
  ): (ContextFieldsToLog, LoggerTakingImplicit[ContextFieldsToLog], String) => PartialFunction[Throwable, Future[T]] =
    (context, logger, msg) => {
      case ex @ ApiError(code, message, _, _, _) if code == 404 =>
        logger.error(s"$msg. code > $code - message > $message", ex)(context)
        Future.failed[T](ResourceNotFoundError(userId))
      case ex @ ApiError(code, message, _, _, _)                =>
        logger.error(s"$msg. code > $code - message > $message", ex)(context)
        Future.failed[T](ThirdPartyCallError(serviceName, ex.getMessage))
      case ex                                                   =>
        logger.error(s"$msg. Error: ${ex.getMessage}", ex)(context)
        Future.failed[T](GenericClientError(ex.getMessage))
    }
}
