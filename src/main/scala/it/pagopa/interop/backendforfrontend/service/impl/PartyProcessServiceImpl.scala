package it.pagopa.interop.backendforfrontend.service.impl
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.PartyProcessService
import it.pagopa.interop.backendforfrontend.service.types.PartyProcessServiceTypes.PartyProcessRelationshipInfo
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withUid
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.{
  GenericClientError,
  ResourceNotFoundError,
  ThirdPartyCallError
}
import it.pagopa.interop.selfcare.partyprocess.client.invoker.ApiInvoker
import it.pagopa.interop.selfcare.partyprocess.client.invoker.ApiKeyValue
import it.pagopa.interop.selfcare.partyprocess.client.api.{ProcessApi, EnumsSerializers}
import it.pagopa.interop.selfcare.partyprocess.client.invoker.ApiError
import it.pagopa.interop.selfcare.partyprocess.client.model.{Institution, PartyRole, RelationshipState}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import akka.actor.typed.ActorSystem

class PartyProcessServiceImpl(partyProcessUrl: String, partyProcessApiKey: String)(implicit system: ActorSystem[_])
    extends PartyProcessService {

  implicit val userRegistryApiKeyValue: ApiKeyValue = ApiKeyValue(partyProcessApiKey)
  val invoker: ApiInvoker                           = ApiInvoker(EnumsSerializers.all)(system.classicSystem)
  val api: ProcessApi                               = ProcessApi(partyProcessUrl)

  private val replacementEntityId: String = "NoIdentifier"
  private val serviceName: String         = "party-process"

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getRelationship(relationshipId: UUID)(implicit
    contexts: Seq[(String, String)],
    ec: ExecutionContext
  ): Future[PartyProcessRelationshipInfo] = withUid { uid =>
    val request = api.getRelationship(relationshipId)(uid)
    invoker.invoke(
      request,
      s"Retrieving relationship $relationshipId",
      invocationRecovery(Some(relationshipId.toString))
    )
  }

  override def getUserInstitutionRelationships(
    selfcareId: String,
    personId: Option[UUID],
    roles: Seq[PartyRole],
    states: Seq[RelationshipState],
    products: Seq[String],
    productRoles: Seq[String]
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Seq[PartyProcessRelationshipInfo]] =
    withUid { uid =>
      selfcareId.toFutureUUID.flatMap { selfcareUUID =>
        val request =
          api.getUserInstitutionRelationships(selfcareUUID, personId, roles, states, products, productRoles)(uid)
        invoker.invoke(request, s"Relationships for institution ${selfcareId}", invocationRecovery(None))
      }
    }

  override def getInstitution(
    selfcareId: String
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution] = withUid { uid =>
    selfcareId.toFutureUUID.flatMap { selfcareUUID =>
      val request = api.getInstitution(selfcareUUID)(uid)
      invoker.invoke(request, s"Institution ${selfcareId}", invocationRecovery(None))
    }
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
