package it.pagopa.interop.backendforfrontend.service.impl

import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.SelfcareV2ClientService
import it.pagopa.interop.selfcare.v2.client.invoker.{ApiInvoker, ApiKeyValue, ApiRequest, ApiError}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.selfcare.v2.client.api.{InstitutionsApi, UsersApi, EnumsSerializers}
import it.pagopa.interop.selfcare.v2.client.model.{
  Institution,
  UserResource,
  InstitutionResource,
  UserResponse,
  ProductResource
}
import it.pagopa.interop.backendforfrontend.error.BFFErrors.{InstitutionNotFound, UserNotFound}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import akka.actor.typed.ActorSystem

class SelfcareV2ClientServiceImpl(selfcareClientServiceURL: String, selfcareClientApiKey: String)(implicit
  system: ActorSystem[_]
) extends SelfcareV2ClientService {

  implicit val apiKeyValue: ApiKeyValue = ApiKeyValue(selfcareClientApiKey)
  val invoker: ApiInvoker               = ApiInvoker(EnumsSerializers.all)(system.classicSystem)
  val institutionsApi: InstitutionsApi  = InstitutionsApi(selfcareClientServiceURL)
  val usersApi: UsersApi                = UsersApi(selfcareClientServiceURL)

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getInstitutionUserProducts(institutionId: UUID, userId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Seq[ProductResource]] = {
    val request =
      institutionsApi.getInstitutionUserProductsUsingGET(
        institutionId = institutionId.toString,
        userId = userId.toString
      )
    invoker.invoke(request, s"Retrieving Products for Institution $institutionId and User $userId")
  }

  override def getInstitutionProductUsers(
    institutionId: UUID,
    requesterId: UUID,
    userId: Option[UUID],
    roles: Seq[String]
  )(implicit contexts: Seq[(String, String)]): Future[Seq[UserResource]] = {
    val request =
      institutionsApi.getInstitutionProductUsersUsingGET(
        institutionId = institutionId.toString,
        userIdForAuth = requesterId.toString,
        userId = userId.map(_.toString),
        productRoles = roles
      )
    invoker.invoke(request, s"Retrieving Users for Institution $institutionId Tenant $requesterId and User $userId")
  }

  override def getInstitutions(
    userId: UUID
  )(implicit contexts: Seq[(String, String)]): Future[Seq[InstitutionResource]] = {
    val request =
      institutionsApi.getInstitutionsUsingGET(userIdForAuth = userId.toString)
    invoker.invoke(request, s"Retrieving Institutions for User $userId")
  }

  override def getInstitution(
    selfcareId: UUID
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution] = {
    val request =
      institutionsApi.getInstitution(id = selfcareId)
    invoker
      .invoke(request, s"Retrieving Institution with id $selfcareId")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 => Future.failed(InstitutionNotFound(selfcareId))
      }
  }

  override def getUserById(selfcareId: UUID, userId: UUID)(implicit
    contexts: Seq[(String, String)],
    ec: ExecutionContext
  ): Future[UserResponse] = {
    val request: ApiRequest[UserResponse] =
      usersApi.getUserInfoUsingGET(id = userId.toString, institutionId = selfcareId.toString.some)
    invoker
      .invoke(request, s"Retrieving User with with istitution id $selfcareId, user $userId")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 => Future.failed(UserNotFound(selfcareId, userId))
      }
  }

}
