package it.pagopa.interop.backendforfrontend

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import com.typesafe.config.{Config, ConfigFactory}
import it.pagopa.interop.backendforfrontend.api.AuthorizationApiService
import it.pagopa.interop.backendforfrontend.api.impl._
import it.pagopa.interop.backendforfrontend.model.{Problem, SessionToken}
import it.pagopa.interop.commons.jwt.service.{JWTReader, SessionTokenGenerator}
import org.scalamock.scalatest.MockFactory
import spray.json.DefaultJsonProtocol
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

  val mockJwtReader: JWTReader                         = mock[JWTReader]
  val mockSessionTokenGenerator: SessionTokenGenerator = mock[SessionTokenGenerator]
  final val bearerToken: String                        = "token"
  val service: AuthorizationApiService                 =
    AuthorizationApiServiceImpl(mockJwtReader, mockSessionTokenGenerator)

  implicit def fromEntityUnmarshallerIdentityToken: FromEntityUnmarshaller[SessionToken] =
    sprayJsonUnmarshaller[SessionToken]

  implicit def fromEntityUnmarshallerProblem: FromEntityUnmarshaller[Problem] =
    sprayJsonUnmarshaller[Problem]

}
