package it.pagopa.interop.backendforfrontend.common.system

import com.typesafe.config.{Config, ConfigFactory}
import it.pagopa.interop.commons.ratelimiter.model.LimiterConfig

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

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

  val signerMaxConnections: Int = config.getInt("backend-for-frontend.signer-max-connections")

  val rateLimiterConfigs: LimiterConfig = {
    val rateInterval = config.getDuration("backend-for-frontend.rate-limiter.rate-interval")

    LimiterConfig(
      limiterGroup = config.getString("backend-for-frontend.rate-limiter.limiter-group"),
      maxRequests = config.getInt("backend-for-frontend.rate-limiter.max-requests"),
      burstPercentage = config.getDouble("backend-for-frontend.rate-limiter.burst-percentage"),
      rateInterval = FiniteDuration(rateInterval.toMillis, TimeUnit.MILLISECONDS),
      redisHost = config.getString("backend-for-frontend.rate-limiter.redis-host"),
      redisPort = config.getInt("backend-for-frontend.rate-limiter.redis-port")
    )
  }

  require(jwtAudience.nonEmpty, "Audience cannot be empty")
  require(generatedJwtAudience.nonEmpty, "Generated JWT audience cannot be empty")
  require(
    rsaKeysIdentifiers.nonEmpty || ecKeysIdentifiers.nonEmpty,
    "You MUST provide at least one signing key (either RSA or EC)"
  )

  val partyProcessURL: String    = config.getString("backend-for-frontend.services.party-process")
  val partyProcessApiKey: String = config.getString("backend-for-frontend.api-keys.party-process")

  val userRegistryURL: String    = config.getString("backend-for-frontend.services.user-registry")
  val userRegistryApiKey: String = config.getString("backend-for-frontend.api-keys.user-registry")

  val attributeRegistryManagementURL: String =
    config.getString("backend-for-frontend.services.attribute-registry-management")

  val agreementProcessURL: String  = config.getString("backend-for-frontend.services.agreement-process")
  val catalogManagementURL: String = config.getString("backend-for-frontend.services.catalog-management")
  val tenantManagementURL: String  = config.getString("backend-for-frontend.services.tenant-management")

}
