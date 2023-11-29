package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.authorizationprocess.client.{model => AuthorizationProcess}
import it.pagopa.interop.backendforfrontend.model._

object AuthorizationProcessServiceTypes {

  implicit class PurposeAdditionDetailsSeedConverter(private val seed: PurposeAdditionDetailsSeed) extends AnyVal {
    def toProcess: AuthorizationProcess.PurposeAdditionDetails =
      AuthorizationProcess.PurposeAdditionDetails(purposeId = seed.purposeId)
  }

  implicit class ClientProcessConverter(private val c: AuthorizationProcess.Client) extends AnyVal {
    def toCreatedResource: CreatedResource            = CreatedResource(id = c.id)
    def toCompactApi(hasKeys: Boolean): CompactClient =
      CompactClient(id = c.id, name = c.name, hasKeys = hasKeys)
  }

  implicit class ClientKindProcessConverter(private val ck: AuthorizationProcess.ClientKind) extends AnyVal {
    def toApi: ClientKind = ck match {
      case AuthorizationProcess.ClientKind.API      => ClientKind.API
      case AuthorizationProcess.ClientKind.CONSUMER => ClientKind.CONSUMER
    }
  }

  implicit class ClientKindConverter(private val ck: ClientKind) extends AnyVal {
    def toProcess: AuthorizationProcess.ClientKind = ck match {
      case ClientKind.API      => AuthorizationProcess.ClientKind.API
      case ClientKind.CONSUMER => AuthorizationProcess.ClientKind.CONSUMER
    }
  }

  implicit class KeyUseConverter(private val ku: KeyUse) extends AnyVal {
    def toProcess: AuthorizationProcess.KeyUse = ku match {
      case KeyUse.SIG => AuthorizationProcess.KeyUse.SIG
      case KeyUse.ENC => AuthorizationProcess.KeyUse.ENC
    }
  }

  implicit class KeySeedConverter(private val seed: KeySeed) extends AnyVal {
    def toProcess: AuthorizationProcess.KeySeed =
      AuthorizationProcess.KeySeed(key = seed.key, use = seed.use.toProcess, alg = seed.alg, name = seed.name)
  }

  implicit class ClientSeedConverter(private val seed: ClientSeed) extends AnyVal {
    def toProcess: AuthorizationProcess.ClientSeed =
      AuthorizationProcess.ClientSeed(name = seed.name, description = seed.description, members = seed.members)
  }

  implicit class CompactClientConverter(private val c: AuthorizationProcess.Client) extends AnyVal {
    def toApi(hasKeys: Boolean): CompactClient =
      CompactClient(id = c.id, name = c.name, hasKeys = hasKeys)
  }

  implicit class ClientWithKeysConverter(private val c: AuthorizationProcess.ClientWithKeys) extends AnyVal {
    def toApi: CompactClient =
      CompactClient(id = c.client.id, name = c.client.name, hasKeys = c.keys.nonEmpty)
  }

  implicit class ReadClientKeyConverter(private val k: AuthorizationProcess.Key) extends AnyVal {
    def toApi(user: CompactUser, isOrphan: Boolean): PublicKey =
      PublicKey(keyId = k.kid, name = k.name, user = user, createdAt = k.createdAt, isOrphan = isOrphan)
  }
}
