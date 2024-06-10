package it.pagopa.interop.backendforfrontend.common.system

import com.typesafe.config.{Config, ConfigFactory}
import it.pagopa.interop.commons.ratelimiter.model.LimiterConfig

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import java.util.UUID

object ApplicationConfiguration {
  val config: Config = ConfigFactory.load()

  val pagoPaTenantId: UUID     = UUID.fromString(config.getString("backend-for-frontend.pagopa-tenant-id"))
  val serverPort: Int          = config.getInt("backend-for-frontend.port")
  val jwtAudience: Set[String] =
    config.getString("backend-for-frontend.jwt.audience").split(",").toSet.filter(_.nonEmpty)

  val clientAssertionAudience: Set[String] =
    config.getString("backend-for-frontend.client-assertion-audience").split(",").toSet.filter(_.nonEmpty)

  val generatedJwtIssuer: String        = config.getString("backend-for-frontend.generated-jwt.issuer")
  val generatedJwtAudience: Set[String] =
    config.getString("backend-for-frontend.generated-jwt.audience").split(",").toSet.filter(_.nonEmpty)
  val generatedJwtDuration: Long        = config.getLong("backend-for-frontend.generated-jwt.duration-seconds")
  val supportLandingJwtDuration: Long   =
    config.getLong("backend-for-frontend.saml2-jwt.support-landing-token-duration-seconds")
  val supportJwtDuration: Long        = config.getLong("backend-for-frontend.saml2-jwt.support-token-duration-seconds")
  val saml2Audience: String           = config.getString("backend-for-frontend.saml2-jwt.audience")
  val saml2CallbackUrl: String        = config.getString("backend-for-frontend.saml2-jwt.callback-url")
  val saml2CallbackErrorUrl: String   = config.getString("backend-for-frontend.saml2-jwt.callback-error-url")
  val rsaKeysIdentifiers: Set[String] =
    config.getString("backend-for-frontend.rsa-keys-identifiers").split(",").toSet.filter(_.nonEmpty)

  val ecKeysIdentifiers: Set[String] =
    config.getString("backend-for-frontend.ec-keys-identifiers").split(",").toSet.filter(_.nonEmpty)

  val signerMaxConnections: Int         = config.getInt("backend-for-frontend.signer-max-connections")
  val tenantAllowedOrigins: Set[String] =
    config.getString("backend-for-frontend.tenant-allowed-origins").split(",").toSet.filter(_.nonEmpty)
  val rateLimiterConfigs: LimiterConfig = {
    val rateInterval = config.getDuration("backend-for-frontend.rate-limiter.rate-interval")
    val timeout      = config.getDuration("backend-for-frontend.rate-limiter.timeout")

    LimiterConfig(
      limiterGroup = config.getString("backend-for-frontend.rate-limiter.limiter-group"),
      maxRequests = config.getInt("backend-for-frontend.rate-limiter.max-requests"),
      burstPercentage = config.getDouble("backend-for-frontend.rate-limiter.burst-percentage"),
      rateInterval = FiniteDuration(rateInterval.toMillis, TimeUnit.MILLISECONDS),
      redisHost = config.getString("backend-for-frontend.rate-limiter.redis-host"),
      redisPort = config.getInt("backend-for-frontend.rate-limiter.redis-port"),
      timeout = FiniteDuration(timeout.toMillis, TimeUnit.MILLISECONDS)
    )
  }

  require(jwtAudience.nonEmpty, "Audience cannot be empty")
  require(generatedJwtAudience.nonEmpty, "Generated JWT audience cannot be empty")
  require(
    rsaKeysIdentifiers.nonEmpty || ecKeysIdentifiers.nonEmpty,
    "You MUST provide at least one signing key (either RSA or EC)"
  )

  val selfcareV2URL: String    = config.getString("backend-for-frontend.services.selfcare-v2")
  val selfcareV2ApiKey: String = config.getString("backend-for-frontend.api-keys.selfcare-v2")

  val attributeRegistryProcessURL: String =
    config.getString("backend-for-frontend.services.attribute-registry-process")
  val agreementProcessURL: String         = config.getString("backend-for-frontend.services.agreement-process")
  val catalogProcessURL: String           = config.getString("backend-for-frontend.services.catalog-process")
  val tenantProcessURL: String            = config.getString("backend-for-frontend.services.tenant-process")
  val purposeProcessURL: String           = config.getString("backend-for-frontend.services.purpose-process")
  val authorizationManagementURL: String  = config.getString("backend-for-frontend.services.authorization-management")
  val authorizationProcessURL: String     = config.getString("backend-for-frontend.services.authorization-process")

  val storageKind: String                    = config.getString("backend-for-frontend.storage.kind")
  val riskAnalysisDocumentsContainer: String =
    config.getString("backend-for-frontend.storage.risk-analysis-documents.container")
  val riskAnalysisDocumentsPath: String  = config.getString("backend-for-frontend.storage.risk-analysis-documents.path")
  val consumerDocumentsContainer: String = config.getString("backend-for-frontend.storage.consumer-documents.container")
  val consumerDocumentsPath: String      = config.getString("backend-for-frontend.storage.consumer-documents.path")
  val eServiceDocumentsContainer: String = config.getString("backend-for-frontend.storage.eservice-documents.container")
  val eServiceDocumentsPath: String      = config.getString("backend-for-frontend.storage.eservice-documents.path")
  val exportEserviceContainer: String    = config.getString("backend-for-frontend.storage.export-eservice.container")
  val exportEservicePath: String         = config.getString("backend-for-frontend.storage.export-eservice.path")
  val getUrlDurationMinutes: Int         =
    config.getInt("backend-for-frontend.storage.export-eservice.storage.get-url-duration-minutes")
  val importEServiceContainer: String    = config.getString("backend-for-frontend.storage.import-eservice.container")
  val importEServicePath: String         = config.getString("backend-for-frontend.storage.import-eservice.path")
  val putUrlDurationMinutes: Int         =
    config.getInt("backend-for-frontend.storage.export-eservice.put-url-duration-minutes")
  val allowListContainer: String         = config.getString("backend-for-frontend.storage.allow-list.container")
  val allowListPath: String              = config.getString("backend-for-frontend.storage.allow-list.path")
  val allowListFilename: String          = config.getString("backend-for-frontend.storage.allow-list.filename")

  val privacyNoticePpUuid: String          = config.getString("backend-for-frontend.privacy-notices.pp-uuid")
  val privacyNoticeTosUuid: String         = config.getString("backend-for-frontend.privacy-notices.tos-uuid")
  val privacyNoticesTableName: String      =
    config.getString("backend-for-frontend.privacy-notices.table-name-privacy-notices")
  val privacyNoticesUsersTableName: String =
    config.getString("backend-for-frontend.privacy-notices.table-name-privacy-notices-users")
  val privacyNoticesContainer: String      = config.getString("backend-for-frontend.privacy-notices.container")
  val privacyNoticesPath: String           = config.getString("backend-for-frontend.privacy-notices.path")
  val privacyNoticesFileName: String       = config.getString("backend-for-frontend.privacy-notices.filename")
}
