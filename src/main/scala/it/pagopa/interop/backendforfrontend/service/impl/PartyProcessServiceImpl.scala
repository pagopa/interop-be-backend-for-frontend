package it.pagopa.interop.backendforfrontend.service.impl
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.PartyProcessService
import it.pagopa.interop.backendforfrontend.service.types.PartyProcessServiceTypes.{
  PartyProcessApiKeyValue,
  PartyProcessInvoker,
  PartyProcessRelationshipInfo
}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.selfcare.partyprocess.client.api.ProcessApi
import it.pagopa.interop.selfcare.partyprocess.client.invoker.ApiKeyValue
import it.pagopa.interop.selfcare.partyprocess.client.model.{PartyRole, RelationshipState}

import java.util.UUID
import scala.concurrent.Future

final case class PartyProcessServiceImpl(invoker: PartyProcessInvoker, partyApi: ProcessApi)
    extends PartyProcessService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getRelationship(relationshipId: UUID)(xSelfCareUID: String)(implicit
    partyProcessApiKeyValue: PartyProcessApiKeyValue,
    contexts: Seq[(String, String)]
  ): Future[PartyProcessRelationshipInfo] = {
    val request = partyApi.getRelationship(relationshipId)(xSelfCareUID)
    invoker.invoke(request, s"Retrieving relationship $relationshipId")
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
    invoker.invoke(request, s"Relationships for institution ${institutionId.toString}")
  }
}
