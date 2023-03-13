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

  implicit class ClientKeyConverter(private val ck: AuthorizationProcess.ClientKey) extends AnyVal {
    def toApi: ClientKey =
      ClientKey(key = ck.key.toApi, name = ck.name, createdAt = ck.createdAt, isOrphan = false)
  }

  implicit class ClientKeysConverter(private val cks: AuthorizationProcess.ClientKeys) extends AnyVal {
    def toApi: ClientKeys =
      ClientKeys(keys = cks.keys.map(_.toApi))
  }

}
