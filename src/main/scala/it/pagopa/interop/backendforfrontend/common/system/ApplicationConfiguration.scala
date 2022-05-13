package it.pagopa.interop.backendforfrontend.common.system

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  val config: Config = ConfigFactory.load()

  val serverPort: Int          = config.getInt("backend-for-frontend.port")
  val jwtAudience: Set[String] =
    config.getString("backend-for-frontend.jwt.audience").split(",").toSet.filter(_.nonEmpty)

  val generatedJwtIssuer: String        = config.getString("backend-for-frontend.generated-jwt.issuer")
  val generatedJwtAudience: Set[String] =
    config.getString("backend-for-frontend.generated-jwt.audience").split(",").toSet.filter(_.nonEmpty)
  val generatedJwtDuration: Long        = config.getLong("backend-for-frontend.generated-jwt.duration-seconds")

  val rsaKeysIdentifiers: Set[String] =
    config.getString("backend-for-frontend.rsa-keys-identifiers").split(",").toSet.filter(_.nonEmpty)

  val ecKeysIdentifiers: Set[String] =
    config.getString("backend-for-frontend.ec-keys-identifiers").split(",").toSet.filter(_.nonEmpty)

  require(jwtAudience.nonEmpty, "Audience cannot be empty")
  require(generatedJwtAudience.nonEmpty, "Generated JWT audience cannot be empty")
  require(
    rsaKeysIdentifiers.nonEmpty || ecKeysIdentifiers.nonEmpty,
    "You MUST provide at least one signing key (either RSA or EC)"
  )

  val partyProcessURL: String    = config.getString("backend-for-frontend.services.party-process.url")
  val partyProcessApiKey: String = config.getString("backend-for-frontend.services.party-process.api-key")

  val userRegistryURL: String    = config.getString("backend-for-frontend.services.user-registry.url")
  val userRegistryApiKey: String = config.getString("backend-for-frontend.services.user-registry.api-key")

}
