package it.pagopa.interop.backendforfrontend.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.UserRegistryService
import it.pagopa.interop.backendforfrontend.service.types.UserRegistryServiceTypes.{
  UserRegistryApiKeyValue,
  UserRegistryInvoker
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.selfcare.userregistry.client.api.UserApi
import it.pagopa.interop.selfcare.userregistry.client.model.UserResource

import java.util.UUID
import scala.concurrent.Future

final case class UserRegistryServiceImpl(invoker: UserRegistryInvoker, userApi: UserApi) extends UserRegistryService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  def findById(userId: UUID)(xSelfCareUID: String)(implicit
    userRegistryApiKeyValue: UserRegistryApiKeyValue,
    contexts: Seq[(String, String)]
  ): Future[UserResource] = {
    val request = userApi.findByIdUsingGET(userId, Seq.empty)(xSelfCareUID)
    invoker.invoke(request, s"Retrieving user ${userId.toString}")
  }
}
