package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.authorizationprocess.client.{model => AuthorizationProcess}
import it.pagopa.interop.backendforfrontend.model.{PurposeAdditionDetailsSeed, CreatedResource}
import it.pagopa.interop.backendforfrontend.model.ClientSeed

object AuthorizationProcessServiceTypes {

  implicit class PurposeAdditionDetailsSeedConverter(private val seed: PurposeAdditionDetailsSeed) extends AnyVal {
    def toProcess: AuthorizationProcess.PurposeAdditionDetails =
      AuthorizationProcess.PurposeAdditionDetails(purposeId = seed.purposeId)
  }

  implicit class ClientConverter(private val client: AuthorizationProcess.Client) extends AnyVal {
    def toCreatedResource: CreatedResource = CreatedResource(id = client.id)
  }

  implicit class ClientSeedConverter(private val seed: ClientSeed) extends AnyVal {
    def toProcess: AuthorizationProcess.ClientSeed =
      AuthorizationProcess.ClientSeed(name = seed.name, description = seed.description)
  }

}
