package it.pagopa.interop.backendforfrontend.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.SelfcareClientService
import it.pagopa.interop.selfcare.v2.client.invoker.{ApiInvoker, ApiKeyValue}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.selfcare.v2.client.api.{InstitutionsApi, EnumsSerializers}
import it.pagopa.interop.selfcare.v2.client.model.ProductResource

import java.util.UUID
import scala.concurrent.Future
import akka.actor.typed.ActorSystem

class SelfcareClientServiceImpl(selfcareClientServiceURL: String, selfcareClientApiKey: String)(implicit
  system: ActorSystem[_]
) extends SelfcareClientService {

  implicit val apiKeyValue: ApiKeyValue = ApiKeyValue(selfcareClientApiKey)
  val invoker: ApiInvoker               = ApiInvoker(EnumsSerializers.all)(system.classicSystem)
  val institutionsApi: InstitutionsApi  = InstitutionsApi(selfcareClientServiceURL)

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getInstitutionUserProducts(userId: UUID, institutionId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Seq[ProductResource]] = {
    val request =
      institutionsApi.getInstitutionUserProductsUsingGET(institutionId = institutionId, userId = userId.toString)
    invoker.invoke(request, s"Retrieving Products for Institution $institutionId and User $userId")
  }

}
