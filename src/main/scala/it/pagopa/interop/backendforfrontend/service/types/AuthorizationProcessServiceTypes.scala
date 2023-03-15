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

  implicit class OperatorRoleConverter(private val opr: AuthorizationProcess.OperatorRole) extends AnyVal {
    def toApi: OperatorRole = opr match {
      case AuthorizationProcess.OperatorRole.MANAGER      => OperatorRole.MANAGER
      case AuthorizationProcess.OperatorRole.DELEGATE     => OperatorRole.DELEGATE
      case AuthorizationProcess.OperatorRole.SUB_DELEGATE => OperatorRole.SUB_DELEGATE
      case AuthorizationProcess.OperatorRole.OPERATOR     => OperatorRole.OPERATOR
    }
  }

  implicit class OperatorStateConverter(private val opr: AuthorizationProcess.OperatorState) extends AnyVal {
    def toApi: OperatorState = opr match {
      case AuthorizationProcess.OperatorState.ACTIVE    => OperatorState.ACTIVE
      case AuthorizationProcess.OperatorState.SUSPENDED => OperatorState.SUSPENDED
      case AuthorizationProcess.OperatorState.DELETED   => OperatorState.DELETED
    }
  }

  implicit class RelationshipProductConverter(private val rp: AuthorizationProcess.RelationshipProduct) extends AnyVal {
    def toApi: RelationshipProduct = RelationshipProduct(id = rp.id, role = rp.role, createdAt = rp.createdAt)
  }

  implicit class OperatorConverter(private val op: AuthorizationProcess.Operator) extends AnyVal {
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

  implicit class EncodedClientKeyConverter(private val eck: AuthorizationProcess.EncodedClientKey) extends AnyVal {
    def toApi: EncodedClientKey = EncodedClientKey(key = eck.key)
  }

  implicit class ClientSeedConverter(private val seed: ClientSeed) extends AnyVal {
    def toProcess: AuthorizationProcess.ClientSeed =
      AuthorizationProcess.ClientSeed(name = seed.name, description = seed.description)
  }

}
