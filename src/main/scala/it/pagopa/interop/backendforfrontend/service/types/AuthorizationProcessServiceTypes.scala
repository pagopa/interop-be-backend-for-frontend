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

  implicit class KeyUseConverter(private val ku: KeyUse)     extends AnyVal {
    def toProcess: AuthorizationProcess.KeyUse = ku match {
      case KeyUse.SIG => AuthorizationProcess.KeyUse.SIG
      case KeyUse.ENC => AuthorizationProcess.KeyUse.ENC
    }
  }
  implicit class KeySeedConverter(private val seed: KeySeed) extends AnyVal {
    def toProcess: AuthorizationProcess.KeySeed =
      AuthorizationProcess.KeySeed(key = seed.key, use = seed.use.toProcess, alg = seed.alg, name = seed.name)
  }
}
