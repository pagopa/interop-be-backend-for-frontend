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

  implicit class OperatorRoleProcessConverter(private val opr: AuthorizationProcess.OperatorRole) extends AnyVal {
    def toApi: OperatorRole = opr match {
      case AuthorizationProcess.OperatorRole.MANAGER      => OperatorRole.MANAGER
      case AuthorizationProcess.OperatorRole.DELEGATE     => OperatorRole.DELEGATE
      case AuthorizationProcess.OperatorRole.SUB_DELEGATE => OperatorRole.SUB_DELEGATE
      case AuthorizationProcess.OperatorRole.OPERATOR     => OperatorRole.OPERATOR
    }
  }

  implicit class OperatorStateProcessConverter(private val opr: AuthorizationProcess.OperatorState) extends AnyVal {
    def toApi: OperatorState = opr match {
      case AuthorizationProcess.OperatorState.ACTIVE    => OperatorState.ACTIVE
      case AuthorizationProcess.OperatorState.SUSPENDED => OperatorState.SUSPENDED
      case AuthorizationProcess.OperatorState.DELETED   => OperatorState.DELETED
    }
  }

  implicit class RelationshipProductProcessConverter(private val rp: AuthorizationProcess.RelationshipProduct)
      extends AnyVal {
    def toApi: RelationshipProduct = RelationshipProduct(id = rp.id, role = rp.role, createdAt = rp.createdAt)
  }

  implicit class OperatorProcessConverter(private val op: AuthorizationProcess.Operator) extends AnyVal {
    def toApi: Operator = Operator(
      relationshipId = op.relationshipId,
      taxCode = op.taxCode,
      name = op.name,
      familyName = op.familyName,
      role = op.role.toApi,
      product = op.product.toApi,
      state = op.state.toApi
    )
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

  implicit class EncodedClientKeyProcessConverter(private val eck: AuthorizationProcess.EncodedClientKey)
      extends AnyVal {
    def toApi: EncodedClientKey = EncodedClientKey(key = eck.key)
  }

  implicit class ClientSeedConverter(private val seed: ClientSeed) extends AnyVal {
    def toProcess: AuthorizationProcess.ClientSeed =
      AuthorizationProcess.ClientSeed(name = seed.name, description = seed.description)
  }

  implicit class CompactClientConverter(private val c: AuthorizationProcess.Client) extends AnyVal {
    def toApi(hasKeys: Boolean): CompactClient =
      CompactClient(id = c.id, name = c.name, hasKeys = hasKeys)
  }

  implicit class OperatorDetailsConverter(private val od: AuthorizationProcess.OperatorDetails) extends AnyVal {
    def toApi: SelfcareUser =
      SelfcareUser(relationshipId = od.relationshipId, familyName = od.familyName, name = od.name)
  }

  implicit class OperatorDetailsConverter(private val od: AuthorizationProcess.OperatorDetails) extends AnyVal {
    def toApi: OperatorDetails =
      OperatorDetails(relationshipId = od.relationshipId, familyName = od.familyName, name = od.name)
  }

  implicit class OtherPrimeInfoConverter(private val opi: AuthorizationProcess.OtherPrimeInfo) extends AnyVal {
    def toApi: OtherPrimeInfo = OtherPrimeInfo(r = opi.r, d = opi.d, t = opi.t)
  }

  implicit class KeyConverter(private val key: AuthorizationProcess.Key) extends AnyVal {
    def toApi: Key =
      Key(
        kty = key.kty,
        key_ops = key.key_ops,
        use = key.use,
        alg = key.alg,
        kid = key.kid,
        x5u = key.x5u,
        x5t = key.x5t,
        x5tS256 = key.x5tS256,
        x5c = key.x5c,
        crv = key.crv,
        x = key.x,
        y = key.y,
        d = key.d,
        k = key.k,
        n = key.n,
        e = key.e,
        p = key.p,
        q = key.q,
        dp = key.dp,
        dq = key.dq,
        qi = key.qi,
        oth = key.oth.map(_.map(_.toApi))
      )
  }

  implicit class ReadClientKeyConverter(private val rck: AuthorizationProcess.ReadClientKey) extends AnyVal {
    def toApi: ReadClientKey =
      ReadClientKey(
        key = rck.key.toApi,
        name = rck.name,
        operator = rck.operator.toApi,
        createdAt = rck.createdAt,
        isOrphan = false
      )
  }
  implicit class ReadClientKeyConverter(private val rck: AuthorizationProcess.ReadClientKey) extends AnyVal {
    def toApi(isOrphan: Boolean): PublicKey =
      PublicKey(
        keyId = rck.key.kid,
        name = rck.name,
        operator = rck.operator.toApi,
        createdAt = rck.createdAt,
        isOrphan = isOrphan
      )
  }
}
