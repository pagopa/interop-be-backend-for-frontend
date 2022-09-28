package it.pagopa.interop.backendforfrontend.server.impl.dependencies

import cats.implicits._
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.http.scaladsl.server.{Directive1, Route}
import com.atlassian.oai.validator.report.ValidationReport
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api._
import it.pagopa.interop.backendforfrontend.api.impl.{
  AgreementsApiMarshallerImpl,
  AgreementsApiServiceImpl,
  AttributesApiMarshallerImpl,
  AttributesApiServiceImpl,
  AuthorizationApiMarshallerImpl,
  AuthorizationApiServiceImpl,
  HealthApiMarshallerImpl,
  HealthServiceApiImpl,
  PartyApiMarshallerImpl,
  PartyApiServiceImpl,
  TenantsApiMarshallerImpl,
  TenantsApiServiceImpl,
  entityMarshallerProblem,
  problemOf
}
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.service._
import it.pagopa.interop.backendforfrontend.service.impl._
import it.pagopa.interop.commons.jwt._
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, DefaultSessionTokenGenerator, getClaimsVerifier}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.ratelimiter.RateLimiter
import it.pagopa.interop.commons.ratelimiter.akkahttp.RateLimiterDirective
import it.pagopa.interop.commons.ratelimiter.impl.RedisRateLimiter
import it.pagopa.interop.commons.signer.service.SignerService
import it.pagopa.interop.commons.signer.service.impl.KMSSignerService
import it.pagopa.interop.commons.utils.TypeConversions.TryOps
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.interop.commons.utils.{AkkaUtils, OpenapiUtils}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import it.pagopa.interop.commons.jwt.service.SessionTokenGenerator
import it.pagopa.interop.backendforfrontend.server.Controller

trait Dependencies {

  private val rateLimiter: RateLimiter =
    RedisRateLimiter(ApplicationConfiguration.rateLimiterConfigs, OffsetDateTimeSupplier)

  private val rateLimiterDirective: ExecutionContext => Seq[(String, String)] => Directive1[Seq[(String, String)]] = {
    val logger: LoggerTakingImplicit[ContextFieldsToLog] = Logger.takingImplicit[ContextFieldsToLog](this.getClass)
    ec =>
      contexts => {
        RateLimiterDirective.rateLimiterDirective(
          rateLimiter,
          problemOf(StatusCodes.TooManyRequests, GenericComponentErrors.TooManyRequests)
        )(contexts)(ec, entityMarshallerProblem, logger)
      }
  }

  private val healthApi: HealthApi = new HealthApi(
    new HealthServiceApiImpl(),
    HealthApiMarshallerImpl,
    SecurityDirectives.authenticateOAuth2("SecurityRealm", AkkaUtils.PassThroughAuthenticator),
    loggingEnabled = false
  )

  private val validationExceptionToRoute: ValidationReport => Route = report => {
    val error = problemOf(StatusCodes.BadRequest, OpenapiUtils.errorFromRequestValidationReport(report))
    complete(error.status, error)(entityMarshallerProblem)
  }

  def getJwtValidator: Future[JWTReader] = JWTConfiguration.jwtReader
    .loadKeyset()
    .map(keyset =>
      new DefaultJWTReader with PublicKeysHolder {
        var publicKeyset: Map[KID, SerializedKey] = keyset

        override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
          getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
      }
    )
    .toFuture

  def makeController(jwtReader: JWTReader, blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_]
  ): Controller = {

    implicit val ec: ExecutionContext = actorSystem.executionContext

    val oauthAndRateLimitingDirective: Directive1[Seq[(String, String)]] =
      jwtReader.OAuth2JWTValidatorAsContexts.flatMap(rateLimiterDirective(ec))

    val partyProcess: PartyProcessService                     =
      new PartyProcessServiceImpl(ApplicationConfiguration.partyProcessURL, ApplicationConfiguration.partyProcessApiKey)
    val attributeRegistry: AttributeRegistryManagementService =
      new AttributeRegistryManagementServiceImpl(ApplicationConfiguration.attributeRegistryManagementURL, blockingEc)
    val agreementProcess: AgreementProcessService             =
      new AgreementProcessServiceImpl(ApplicationConfiguration.agreementProcessURL, blockingEc)
    val catalogManagement: CatalogManagementService           =
      new CatalogManagementServiceImpl(ApplicationConfiguration.catalogManagementURL, blockingEc)
    val tenantManagement: TenantManagementService             =
      new TenantManagementServiceImpl(ApplicationConfiguration.tenantManagementURL, blockingEc)
    val userRegistry: UserRegistryService                     =
      new UserRegistryServiceImpl(ApplicationConfiguration.userRegistryURL, ApplicationConfiguration.userRegistryApiKey)
    val tenantProcess: TenantProcessService                   = new TenantProcessServiceImpl(null, null)

    val signerService: SignerService = new KMSSignerService(blockingEc)

    val sessionTokenGenerator: SessionTokenGenerator = new DefaultSessionTokenGenerator(
      signerService,
      new PrivateKeysKidHolder {
        override val RSAPrivateKeyset: Set[KID] = ApplicationConfiguration.rsaKeysIdentifiers
        override val ECPrivateKeyset: Set[KID]  = ApplicationConfiguration.ecKeysIdentifiers
      }
    )

    val authorizationApi: AuthorizationApi = new AuthorizationApi(
      AuthorizationApiServiceImpl(
        jwtReader,
        sessionTokenGenerator,
        tenantManagement,
        tenantProcess,
        partyProcess,
        rateLimiter
      ),
      AuthorizationApiMarshallerImpl,
      SecurityDirectives.authenticateOAuth2("SecurityRealm", AkkaUtils.PassThroughAuthenticator)
    )

    val partyApi: PartyApi = new PartyApi(
      PartyApiServiceImpl(partyProcess, userRegistry, attributeRegistry),
      PartyApiMarshallerImpl,
      oauthAndRateLimitingDirective
    )

    val attributesApi: AttributesApi = new AttributesApi(
      AttributesApiServiceImpl(attributeRegistry),
      AttributesApiMarshallerImpl,
      oauthAndRateLimitingDirective
    )

    val agreementsApi: AgreementsApi = new AgreementsApi(
      AgreementsApiServiceImpl(agreementProcess, attributeRegistry, catalogManagement, partyProcess, tenantManagement),
      AgreementsApiMarshallerImpl,
      oauthAndRateLimitingDirective
    )

    val tenantsApi: TenantsApi = new TenantsApi(
      TenantsApiServiceImpl(attributeRegistry, tenantManagement, tenantProcess),
      TenantsApiMarshallerImpl,
      oauthAndRateLimitingDirective
    )

    new Controller(
      attributes = attributesApi,
      authorization = authorizationApi,
      agreements = agreementsApi,
      tenants = tenantsApi,
      party = partyApi,
      health = healthApi,
      validationExceptionToRoute = validationExceptionToRoute.some
    )(actorSystem.classicSystem)
  }

}
