package it.pagopa.interop.backendforfrontend.common.system

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters._

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  lazy val serverPort: Int          = config.getInt("backend-for-frontend.port")
  lazy val jwtAudience: Set[String] = config.getStringList("backend-for-frontend.jwt.audience").asScala.toSet

  lazy val rsaPrivatePath: String       = config.getString("backend-for-frontend.rsa-private-path")
  lazy val interopIdIssuer: String      = config.getString("backend-for-frontend.jwt.issuer")
  lazy val interopAudience: Set[String] = config.getStringList("backend-for-frontend.jwt.audience").asScala.toSet
  lazy val interopTokenDuration: Long   = config.getLong("backend-for-frontend.jwt.duration-seconds")

}
