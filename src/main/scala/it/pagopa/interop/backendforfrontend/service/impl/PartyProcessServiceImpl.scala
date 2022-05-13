package it.pagopa.interop.backendforfrontend.service.impl
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.PartyProcessService
import it.pagopa.interop.backendforfrontend.service.types.PartyProcessServiceTypes.{
  PartyProcessApiKeyValue,
  PartyProcessInvoker,
  PartyProcessRelationshipInfo
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.{
  GenericClientError,
  ResourceNotFoundError,
  ThirdPartyCallError
}
import it.pagopa.interop.selfcare.partyprocess.client.api.ProcessApi
import it.pagopa.interop.selfcare.partyprocess.client.invoker.{ApiError, ApiKeyValue}
import it.pagopa.interop.selfcare.partyprocess.client.model.{PartyRole, RelationshipState}

import java.util.UUID
import scala.concurrent.Future

final case class PartyProcessServiceImpl(invoker: PartyProcessInvoker, partyApi: ProcessApi)
    extends PartyProcessService {

  private val replacementEntityId: String = "NoIdentifier"
  private val serviceName: String         = "party-process"

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getRelationship(relationshipId: UUID)(xSelfCareUID: String)(implicit
    partyProcessApiKeyValue: PartyProcessApiKeyValue,
    contexts: Seq[(String, String)]
  ): Future[PartyProcessRelationshipInfo] = {
    val request = partyApi.getRelationship(relationshipId)(xSelfCareUID)
    invoker.invoke(
      request,
      s"Retrieving relationship $relationshipId",
      invocationRecovery(Some(relationshipId.toString))
    )
  }

  override def getUserInstitutionRelationships(
    institutionId: UUID,
    personId: Option[UUID],
    roles: Seq[PartyRole],
    states: Seq[RelationshipState],
    products: Seq[String],
    productRoles: Seq[String]
  )(xSelfCareUID: String)(implicit
    partyProcessApiKeyValue: ApiKeyValue,
    contexts: Seq[(String, String)]
  ): Future[Seq[PartyProcessRelationshipInfo]] = {
    val request =
      partyApi.getUserInstitutionRelationships(institutionId, personId, roles, states, products, productRoles)(
        xSelfCareUID
      )
    invoker.invoke(request, s"Relationships for institution ${institutionId.toString}", invocationRecovery(None))
  }

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
