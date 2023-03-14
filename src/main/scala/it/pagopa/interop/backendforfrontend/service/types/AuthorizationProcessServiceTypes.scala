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
    def toApi(isOrphan: Boolean): ReadClientKey =
      ReadClientKey(
        key = rck.key.toApi,
        name = rck.name,
        operator = rck.operator.toApi,
        createdAt = rck.createdAt,
        isOrphan = isOrphan
      )
  }
}
