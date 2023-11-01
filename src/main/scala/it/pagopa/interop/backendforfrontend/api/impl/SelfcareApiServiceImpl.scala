package it.pagopa.interop.backendforfrontend.api.impl

import cats.syntax.all._
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.SelfcareApiService
import it.pagopa.interop.backendforfrontend.service.{SelfcareV2ClientService, TenantProcessService}
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.commons.utils.AkkaUtils._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.GenericError
import it.pagopa.interop.backendforfrontend.error.BFFErrors._
import it.pagopa.interop.backendforfrontend.service.types.SelfcareV2ClientServiceTypes._
import it.pagopa.interop.backendforfrontend.common.HeaderUtils._
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.{StatusCodes, HttpHeader}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure}

final case class SelfcareApiServiceImpl(
  selfcareV2ClientService: SelfcareV2ClientService,
  tenantProcessService: TenantProcessService
)(implicit ec: ExecutionContext)
    extends SelfcareApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getInstitutionUserProducts()(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerSelfcareProductarray: ToEntityMarshaller[Seq[SelfcareProduct]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Seq[SelfcareProduct]] =
      for {
        userUuid         <- getUidFutureUUID(contexts)
        organizationUuId <- getOrganizationIdFutureUUID(contexts)
        _ = logger.info(
          s"Retrieving products for institution ${organizationUuId.toString} of user ${userUuid.toString}"
        )
        selfcareUuid <- getSelfcareIdFutureUUID(contexts)
        products     <- selfcareV2ClientService
          .getInstitutionUserProducts(institutionId = selfcareUuid, userId = userUuid)
        productsApi  <- products.traverse(_.toApi).toFuture
      } yield productsApi

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving products for institution", headers) orElse { case Success(resources) =>
        getInstitutionUserProducts200(headers)(resources)
      }
    }
  }

  override def getInstitutions()(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerSelfcareInstitutionarray: ToEntityMarshaller[Seq[SelfcareInstitution]],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Seq[SelfcareInstitution]] =
      for {
        userUuid        <- getUidFutureUUID(contexts)
        institutions    <- selfcareV2ClientService.getInstitutions(userId = userUuid)
        institutionsApi <- institutions.traverse(_.toApi).toFuture  
      } yield institutionsApi

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving institutions", headers) orElse { case Success(resources) =>
        getInstitutions200(headers)(resources)
      }
    }
  }

  override def getUser(userId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerUserInfo: ToEntityMarshaller[TenantUser],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Retrieving user $userId")
    val headers: List[HttpHeader] = headersFromContext()

    val result: Future[TenantUser] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      personId       <- getUidFutureUUID(contexts)
      userUuid       <- userId.toFutureUUID
      selfcareUuid   <- getSelfcareIdFutureUUID(contexts)
      users          <- selfcareV2ClientService
        .getInstitutionProductUsers(
          institutionId = selfcareUuid,
          userId = userUuid.some,
          requesterId = personId,
          roles = Seq.empty
        )
      usersInfo      <- users.traverse(_.toApi(organizationId)).toFuture
      userInfo       <- usersInfo.headOption.toFuture(UserNotFound(selfcareUuid, userUuid))
    } yield userInfo

    onComplete(result) {
      case Success(userInfo)         => getUser200(headers)(userInfo)
      case Failure(ex: UserNotFound) =>
        logger.error(s"Error while retrieving user $userId - ${ex.getMessage}")
        getUser404(headers)(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex)               =>
        logger.error(s"Error while retrieving user $userId - ${ex.getMessage}")
        complete(
          StatusCodes.InternalServerError,
          headers,
          problemOf(
            StatusCodes.InternalServerError,
            GenericError(s"Something went wrong trying to get user $userId - ${ex.getMessage}")
          )
        )
    }
  }

  override def getInstitutionUsers(personId: Option[String], roles: String, query: Option[String], tenantId: String)(
    implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerUserInfoarray: ToEntityMarshaller[Seq[TenantUser]]
  ): Route = {
    logger.info(s"Retrieving users for institutions $tenantId")
    val headers: List[HttpHeader] = headersFromContext()

    def filterByUserName(users: Seq[TenantUser], query: Option[String]): Seq[TenantUser] = {
      query.fold(users)(q =>
        users.filter(user =>
          user.name.toLowerCase.contains(q.toLowerCase) || user.familyName.toLowerCase.contains(q.toLowerCase)
        )
      )
    }

    val result: Future[Seq[TenantUser]] = for {
      userUuid <- personId.traverse(_.toFutureUUID)
      rolesParams = parseArrayParameters(roles)
      tenantUuid     <- tenantId.toFutureUUID
      tenant         <- tenantProcessService.getTenant(tenantUuid)
      selfcareId     <- tenant.selfcareId.toFuture(MissingSelfcareId(tenant.id))
      selfcareUuid   <- selfcareId.toFutureUUID
      requesterUserId  <- getUidFutureUUID(contexts)
      users          <- selfcareV2ClientService
        .getInstitutionProductUsers(
          institutionId = selfcareUuid,
          userId = userUuid,
          requesterId = requesterOrgId,
          roles = rolesParams
        )
      usersApi       <- users.traverse(_.toApi(tenantUuid)).toFuture
    } yield filterByUserName(usersApi, query)

    onComplete(result) {
      case Success(usersInfo) =>
        getInstitutionUsers200(headers)(usersInfo)
      case Failure(ex)        =>
        logger.error(s"Error while retrieving users corresponding to tenant $tenantId - ${ex.getMessage}")
        complete(
          StatusCodes.InternalServerError,
          headers,
          problemOf(
            StatusCodes.InternalServerError,
            GenericError(
              s"Something went wrong trying to get user info for institution corresponding to tenant $tenantId - ${ex.getMessage}"
            )
          )
        )
    }
  }
}
