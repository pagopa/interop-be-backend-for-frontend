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

  implicit class OperatorDetailsConverter(private val od: AuthorizationProcess.OperatorDetails) extends AnyVal {
    def toApi: SelfcareUser =
      SelfcareUser(relationshipId = od.relationshipId, familyName = od.familyName, name = od.name)
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
