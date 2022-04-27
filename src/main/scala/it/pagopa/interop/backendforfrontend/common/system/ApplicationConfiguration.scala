package it.pagopa.interop.backendforfrontend.common.system

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  val config: Config = ConfigFactory.load()

  val serverPort: Int          = config.getInt("backend-for-frontend.port")
  val jwtAudience: Set[String] =
    config.getString("backend-for-frontend.jwt.audience").split(",").toSet.filter(_.nonEmpty)

  val rsaPrivatePath: String            = config.getString("backend-for-frontend.rsa-private-path")
  val generatedJwtIssuer: String        = config.getString("backend-for-frontend.generated-jwt.issuer")
  val generatedJwtAudience: Set[String] =
    config.getString("backend-for-frontend.generated-jwt.audience").split(",").toSet.filter(_.nonEmpty)
  val generatedJwtDuration: Long        = config.getLong("backend-for-frontend.generated-jwt.duration-seconds")

  require(jwtAudience.nonEmpty, "Audience cannot be empty")
  require(generatedJwtAudience.nonEmpty, "Generated JWT audience cannot be empty")
}
