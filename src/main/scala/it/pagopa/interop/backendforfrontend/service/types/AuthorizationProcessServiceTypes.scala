package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.authorizationprocess.client.{model => AuthorizationProcess}
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.selfcare.userregistry.client.model.UserResource

import java.util.UUID

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

  implicit class OperatorDetailsConverter(private val u: UserResource) extends AnyVal {
    def toApi(relationshipId: UUID): SelfcareUser = {
      (u.name, u.familyName) match {
        case (None, None) => SelfcareUser(relationshipId = relationshipId, name = "Utente", familyName = u.id.toString)
        case _            =>
          SelfcareUser(
            relationshipId = relationshipId,
            name = u.name.map(_.value).getOrElse(""),
            familyName = u.familyName.map(_.value).getOrElse("")
          )
      }
    }
  }

  implicit class ReadClientKeyConverter(private val k: AuthorizationProcess.Key) extends AnyVal {
    def toApi(isOrphan: Boolean, user: UserResource): PublicKey =
      PublicKey(
        keyId = k.kid,
        name = k.name,
        operator = user.toApi(k.relationshipId),
        createdAt = k.createdAt,
        isOrphan = isOrphan
      )
  }
}
