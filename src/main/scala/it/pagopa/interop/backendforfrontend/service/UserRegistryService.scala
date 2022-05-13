package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.backendforfrontend.service.types.UserRegistryServiceTypes.UserRegistryApiKeyValue
import it.pagopa.interop.selfcare.userregistry.client.model.UserResource

import java.util.UUID
import scala.concurrent.Future

trait UserRegistryService {

  def findById(userId: UUID)(
    xSelfCareUID: String
  )(implicit userRegistryApiKeyValue: UserRegistryApiKeyValue, contexts: Seq[(String, String)]): Future[UserResource]

}
