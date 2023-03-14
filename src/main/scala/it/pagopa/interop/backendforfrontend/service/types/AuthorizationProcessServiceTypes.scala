package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.authorizationprocess.client.{model => AuthorizationProcess}
import it.pagopa.interop.backendforfrontend.model._

object AuthorizationProcessServiceTypes {

  implicit class PurposeAdditionDetailsSeedConverter(private val seed: PurposeAdditionDetailsSeed) extends AnyVal {
    def toProcess: AuthorizationProcess.PurposeAdditionDetails =
      AuthorizationProcess.PurposeAdditionDetails(purposeId = seed.purposeId)
  }

  implicit class ClientConverter(private val client: AuthorizationProcess.Client) extends AnyVal {
    def toCreatedResource: CreatedResource = CreatedResource(id = client.id)
  }

  implicit class ClientEntryConverter(private val entry: AuthorizationProcess.ClientEntry) extends AnyVal {
    def toApi: CompactClient =
      CompactClient(id = entry.id, name = entry.name)
  }
}
