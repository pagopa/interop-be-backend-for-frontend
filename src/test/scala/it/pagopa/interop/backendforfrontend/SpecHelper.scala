package it.pagopa.interop.backendforfrontend

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import com.nimbusds.jose.Payload
import com.nimbusds.jose.util.{Base64URL, JSONArrayUtils, JSONObjectUtils}
import com.nimbusds.jwt.JWTClaimsSet
import com.typesafe.config.{Config, ConfigFactory}
import it.pagopa.interop.backendforfrontend.api.impl._
import it.pagopa.interop.backendforfrontend.api.{AuthorizationApiService, SupportApiService}
import it.pagopa.interop.backendforfrontend.model.{Problem, SessionToken}
import it.pagopa.interop.backendforfrontend.service.{
  AuthorizationProcessService,
  PartyProcessService,
  TenantProcessService
}
import it.pagopa.interop.commons.jwt.model.Token
import it.pagopa.interop.commons.jwt.service.{InteropTokenGenerator, JWTReader, SessionTokenGenerator}
import it.pagopa.interop.commons.ratelimiter.RateLimiter
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.interop.commons.utils.{ORGANIZATION_ID_CLAIM, USER_ROLES}
import org.scalamock.scalatest.MockFactory
import spray.json.DefaultJsonProtocol

import java.util.UUID
import java.{util => ju}
import scala.concurrent.ExecutionContext.Implicits.global

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

  val mockDateTimeSupplier: OffsetDateTimeSupplier          = mock[OffsetDateTimeSupplier]
  val mockJwtReader: JWTReader                              = mock[JWTReader]
  val mockSessionTokenGenerator: SessionTokenGenerator      = mock[SessionTokenGenerator]
  val mockInteropTokenGenerator: InteropTokenGenerator      = mock[InteropTokenGenerator]
  val mockRateLimiter: RateLimiter                          = mock[RateLimiter]
  val mockTenantProcess: TenantProcessService               = mock[TenantProcessService]
  val mockPartyProcess: PartyProcessService                 = mock[PartyProcessService]
  val mockAuthorizationProcess: AuthorizationProcessService = mock[AuthorizationProcessService]
  final val allowList: List[String]                 = List(UUID.randomUUID().toString, UUID.randomUUID().toString)
  final val bearerToken: String                     = "token"
  val authorizationService: AuthorizationApiService = AuthorizationApiServiceImpl(
    mockJwtReader,
    mockSessionTokenGenerator,
    mockInteropTokenGenerator,
    mockTenantProcess,
    mockPartyProcess,
    mockDateTimeSupplier,
    allowList,
    mockRateLimiter
  )
  val supportService: SupportApiService             =
    SupportApiServiceImpl(mockSessionTokenGenerator, mockTenantProcess, mockDateTimeSupplier)

  implicit def fromEntityUnmarshallerIdentityToken: FromEntityUnmarshaller[SessionToken] =
    sprayJsonUnmarshaller[SessionToken]

  implicit def fromEntityUnmarshallerProblem: FromEntityUnmarshaller[Problem] =
    sprayJsonUnmarshaller[Problem]

  implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  implicit def toEntityMarshallerSessionToken: ToEntityMarshaller[SessionToken] = sprayJsonMarshaller[SessionToken]

  implicit def contexts: Seq[(String, String)] =
    Seq("bearer" -> bearerToken, USER_ROLES -> "admin", ORGANIZATION_ID_CLAIM -> UUID.randomUUID.toString)

//  def desiredClaimSet(tenantId: UUID): Map[String, AnyRef] =
//    Map("uid" -> "support", "user-roles" -> "support", "organizationId" -> tenantId.toString).widen[AnyRef]
//
//  def desiredClaimSet(tenantId: UUID, selfcareId: UUID): Map[String, AnyRef] =
//    Map(
//      "uid"            -> "support",
//      "user-roles"     -> "support",
//      "organizationId" -> tenantId.toString,
//      "selfcareId"     -> selfcareId.toString,
//      "organization"   -> Organization(
//        id = selfcareId.toString,
//        name = "PagoPa",
//        roles = Seq(Role(partyRole = "OPERATOR", role = "support"))
//      ).toJson.asJsObject
//    ).widen[AnyRef]

  final val emptyRelayState: String = ""
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
