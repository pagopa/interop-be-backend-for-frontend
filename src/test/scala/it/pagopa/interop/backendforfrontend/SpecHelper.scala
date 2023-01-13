package it.pagopa.interop.backendforfrontend

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import com.typesafe.config.{Config, ConfigFactory}
import it.pagopa.interop.backendforfrontend.api.AuthorizationApiService
import it.pagopa.interop.backendforfrontend.api.impl._
import it.pagopa.interop.backendforfrontend.model.{Problem, SessionToken}
import it.pagopa.interop.commons.jwt.service.{InteropTokenGenerator, JWTReader, SessionTokenGenerator}
import org.scalamock.scalatest.MockFactory
import spray.json.DefaultJsonProtocol

import scala.concurrent.ExecutionContext.Implicits.global
import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jose.util.JSONArrayUtils

import java.{util => ju}
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jose.Payload
import com.nimbusds.jose.util.Base64URL
import it.pagopa.interop.commons.ratelimiter.RateLimiter
import it.pagopa.interop.backendforfrontend.service.TenantManagementService
import it.pagopa.interop.backendforfrontend.service.TenantProcessService
import it.pagopa.interop.backendforfrontend.service.PartyProcessService
import it.pagopa.interop.commons.jwt.model.Token

import java.util.UUID

trait SpecHelper extends SprayJsonSupport with DefaultJsonProtocol with MockFactory {

  val testData = ConfigFactory.parseString(s"""
      akka.actor.provider = cluster

      akka.remote.classic.netty.tcp.port = 0
      akka.remote.artery.canonical.port = 0
      akka.remote.artery.canonical.hostname = 127.0.0.1

      akka.cluster.jmx.multi-mbeans-in-same-jvm = on

      akka.cluster.sharding.number-of-shards = 10

      akka.coordinated-shutdown.terminate-actor-system = off
      akka.coordinated-shutdown.run-by-actor-system-terminate = off
      akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      akka.cluster.run-coordinated-shutdown-when-down = off
    """)

  val config: Config = ConfigFactory
    .parseResourcesAnySyntax("application-test")
    .withFallback(testData)

  val mockJwtReader: JWTReader                         = mock[JWTReader]
  val mockSessionTokenGenerator: SessionTokenGenerator = mock[SessionTokenGenerator]
  val mockInteropTokenGenerator: InteropTokenGenerator = mock[InteropTokenGenerator]
  val mockRateLimiter: RateLimiter                     = mock[RateLimiter]
  val mockTenantManagement: TenantManagementService    = mock[TenantManagementService]
  val mockTenantProcess: TenantProcessService          = mock[TenantProcessService]
  val mockPartyProcess: PartyProcessService            = mock[PartyProcessService]
  final val allowList: List[String]                    = Nil
  final val bearerToken: String                        = "token"
  val service: AuthorizationApiService                 = AuthorizationApiServiceImpl(
    mockJwtReader,
    mockSessionTokenGenerator,
    mockInteropTokenGenerator,
    mockTenantManagement,
    mockTenantProcess,
    mockPartyProcess,
    allowList,
    mockRateLimiter
  )

  implicit def fromEntityUnmarshallerIdentityToken: FromEntityUnmarshaller[SessionToken] =
    sprayJsonUnmarshaller[SessionToken]

  implicit def fromEntityUnmarshallerProblem: FromEntityUnmarshaller[Problem] =
    sprayJsonUnmarshaller[Problem]
}

object SpecHelper {
  implicit class MapConverter(private val map: Map[String, Object]) extends AnyVal {
    def toJSONObject: ju.Map[String, Object] = {
      val obj = JSONObjectUtils.newJSONObject()
      map.toList.foreach {
        case (k, v: List[_])   => obj.put(k, v.asInstanceOf[List[Object]].toJsonArray)
        case (k, v: Map[_, _]) => obj.put(k, v.asInstanceOf[Map[String, Object]].toJSONObject)
        case (k, v)            => obj.put(k, v)
      }
      obj
    }

    def asClaimSet: JWTClaimsSet = {
      val asBase64Url: Base64URL                    = new Payload(toJSONObject).toBase64URL()
      val asJsonObjectAgain: ju.Map[String, Object] = new Payload(asBase64Url).toJSONObject()
      JWTClaimsSet.parse(asJsonObjectAgain)
    }
  }

  implicit class ListConverter(private val list: List[Object]) extends AnyVal {
    def toJsonArray: ju.List[Object] = {
      val obj = JSONArrayUtils.newJSONArray()
      list.foreach {
        case x: Map[_, _] => obj.add(x.asInstanceOf[Map[String, Object]].toJSONObject)
        case x: List[_]   => obj.add(x.asInstanceOf[List[Object]].toJsonArray)
        case x            => obj.add(x)
      }
      obj
    }
  }

  final val internalToken = Token(
    serialized = UUID.randomUUID().toString,
    jti = UUID.randomUUID().toString,
    iat = 999999L,
    exp = 999999L,
    nbf = 999999L,
    expIn = 999999L,
    alg = UUID.randomUUID().toString,
    kid = UUID.randomUUID().toString,
    aud = List(UUID.randomUUID().toString),
    sub = UUID.randomUUID().toString,
    iss = UUID.randomUUID().toString
  )

}
