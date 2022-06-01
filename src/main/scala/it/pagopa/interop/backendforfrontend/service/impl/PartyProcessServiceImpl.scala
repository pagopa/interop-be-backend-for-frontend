package it.pagopa.interop.backendforfrontend.service.impl
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.PartyProcessService
import it.pagopa.interop.backendforfrontend.service.types.PartyProcessServiceTypes.{
  PartyProcessApiKeyValue,
  PartyProcessInvoker,
  PartyProcessRelationshipInfo
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getUidFuture
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.{
  GenericClientError,
  ResourceNotFoundError,
  ThirdPartyCallError
}
import it.pagopa.interop.selfcare.partyprocess.client.api.ProcessApi
import it.pagopa.interop.selfcare.partyprocess.client.invoker.ApiError
import it.pagopa.interop.selfcare.partyprocess.client.model.{Institution, PartyRole, RelationshipState}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class PartyProcessServiceImpl(invoker: PartyProcessInvoker, partyApi: ProcessApi)(implicit
  partyProcessApiKeyValue: PartyProcessApiKeyValue
) extends PartyProcessService {

  private val replacementEntityId: String = "NoIdentifier"
  private val serviceName: String         = "party-process"

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getRelationship(
    relationshipId: UUID
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[PartyProcessRelationshipInfo] = for {
    uid <- getUidFuture(contexts)
    request = partyApi.getRelationship(relationshipId)(uid)
    result <- invoker.invoke(
      request,
      s"Retrieving relationship $relationshipId",
      invocationRecovery(Some(relationshipId.toString))
    )
  } yield result

  override def getUserInstitutionRelationships(
    institutionId: UUID,
    personId: Option[UUID],
    roles: Seq[PartyRole],
    states: Seq[RelationshipState],
    products: Seq[String],
    productRoles: Seq[String]
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Seq[PartyProcessRelationshipInfo]] = for {
    uid <- getUidFuture(contexts)
    request = partyApi.getUserInstitutionRelationships(institutionId, personId, roles, states, products, productRoles)(
      uid
    )
    result <- invoker.invoke(
      request,
      s"Relationships for institution ${institutionId.toString}",
      invocationRecovery(None)
    )
  } yield result

  override def getInstitution(
    institutionId: UUID
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution] = for {
    uid <- getUidFuture(contexts)
    request = partyApi.getInstitution(institutionId)(uid)
    result <- invoker.invoke(request, s"Institution ${institutionId.toString}", invocationRecovery(None))
  } yield result

  private def invocationRecovery[T](
    entityId: Option[String]
  ): (ContextFieldsToLog, LoggerTakingImplicit[ContextFieldsToLog], String) => PartialFunction[Throwable, Future[T]] =
    (context, logger, msg) => {
      case ex @ ApiError(code, message, _, _, _) if code == 404 =>
        logger.error(s"$msg. code > $code - message > $message", ex)(context)
        Future.failed[T](ResourceNotFoundError(entityId.getOrElse(replacementEntityId)))
      case ex @ ApiError(code, message, _, _, _)                =>
        logger.error(s"$msg. code > $code - message > $message", ex)(context)
        Future.failed[T](ThirdPartyCallError(serviceName, ex.getMessage))
      case ex                                                   =>
        logger.error(s"$msg. Error: ${ex.getMessage}", ex)(context)
        Future.failed[T](GenericClientError(ex.getMessage))
    }

}
