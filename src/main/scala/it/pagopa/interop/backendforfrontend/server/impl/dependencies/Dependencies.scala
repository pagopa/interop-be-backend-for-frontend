package it.pagopa.interop.backendforfrontend.server.impl.dependencies

import akka.actor.typed.ActorSystem
import akka.actor.{ActorSystem => ClassicActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.http.scaladsl.server.{Directive1, Route}
import com.atlassian.oai.validator.report.ValidationReport
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop._
import it.pagopa.interop.agreementprocess.client.api.AgreementApi
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
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
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.{
  AgreementProcessInvoker,
  AttributeRegistryManagementInvoker,
  CatalogManagementInvoker,
  TenantManagementInvoker,
  TenantProcessInvoker
}
import it.pagopa.interop.backendforfrontend.service.types.PartyProcessServiceTypes.{
  PartyProcessApiKeyValue,
  PartyProcessInvoker
}
import it.pagopa.interop.backendforfrontend.service.types.UserRegistryServiceTypes.{
  UserRegistryApiKeyValue,
  UserRegistryInvoker
}
import it.pagopa.interop.catalogmanagement.client.api.EServiceApi
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
import it.pagopa.interop.commons.utils.service.impl.OffsetDateTimeSupplierImpl
import it.pagopa.interop.commons.utils.{AkkaUtils, OpenapiUtils}
import it.pagopa.interop.selfcare.partyprocess.client.api.ProcessApi
import it.pagopa.interop.selfcare.userregistry.client.api.UserApi
import it.pagopa.interop.selfcare.{partyprocess, userregistry}
import it.pagopa.interop.tenantmanagement.client.api.{TenantApi => TenantManagementApi}
import it.pagopa.interop.tenantprocess.client.api.{TenantApi => TenantProcessApi}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait Dependencies {

  implicit val partyProcessApiKeyValue: PartyProcessApiKeyValue =
    partyprocess.client.invoker.ApiKeyValue(ApplicationConfiguration.partyProcessApiKey)

  implicit val userRegistryApiKeyValue: UserRegistryApiKeyValue =
    userregistry.client.invoker.ApiKeyValue(ApplicationConfiguration.userRegistryApiKey)

  val rateLimiter: RateLimiter =
    RedisRateLimiter(ApplicationConfiguration.rateLimiterConfigs, OffsetDateTimeSupplierImpl)

  val rateLimiterDirective: ExecutionContext => Seq[(String, String)] => Directive1[Seq[(String, String)]] = {
    val logger: LoggerTakingImplicit[ContextFieldsToLog] = Logger.takingImplicit[ContextFieldsToLog](this.getClass)
    ec =>
      contexts => {
        RateLimiterDirective.rateLimiterDirective(
          rateLimiter,
          problemOf(StatusCodes.TooManyRequests, GenericComponentErrors.TooManyRequests)
        )(contexts)(ec, entityMarshallerProblem, logger)
      }
  }

  def partyProcess(implicit actorSystem: ActorSystem[_]): PartyProcessService =
    PartyProcessServiceImpl(
      PartyProcessInvokerInvoker()(actorSystem.classicSystem),
      PartyProcessApi(ApplicationConfiguration.partyProcessURL)
    )

  object PartyProcessInvokerInvoker {
    def apply()(implicit actorSystem: ClassicActorSystem): PartyProcessInvoker =
      partyprocess.client.invoker.ApiInvoker(partyprocess.client.api.EnumsSerializers.all)
  }

  object PartyProcessApi {
    def apply(baseUrl: String): ProcessApi = ProcessApi(baseUrl)
  }

  object AttributeRegistryManagementInvoker {
    def apply(
      blockingEc: ExecutionContextExecutor
    )(implicit actorSystem: ClassicActorSystem): AttributeRegistryManagementInvoker =
      attributeregistrymanagement.client.invoker
        .ApiInvoker(attributeregistrymanagement.client.api.EnumsSerializers.all, blockingEc)(actorSystem.classicSystem)
  }

  object AgreementProcessInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ClassicActorSystem): AgreementProcessInvoker =
      agreementprocess.client.invoker
        .ApiInvoker(agreementprocess.client.api.EnumsSerializers.all, blockingEc)(actorSystem.classicSystem)
  }

  object CatalogManagementInvoker {
    def apply(
      blockingEc: ExecutionContextExecutor
    )(implicit actorSystem: ClassicActorSystem): CatalogManagementInvoker =
      catalogmanagement.client.invoker
        .ApiInvoker(catalogmanagement.client.api.EnumsSerializers.all, blockingEc)(actorSystem.classicSystem)
  }

  object TenantManagementInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ClassicActorSystem): TenantManagementInvoker =
      tenantmanagement.client.invoker
        .ApiInvoker(tenantmanagement.client.api.EnumsSerializers.all, blockingEc)(actorSystem.classicSystem)
  }

  object TenantProcessInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ClassicActorSystem): TenantProcessInvoker =
      tenantprocess.client.invoker
        .ApiInvoker(tenantprocess.client.api.EnumsSerializers.all, blockingEc)(actorSystem.classicSystem)
  }

  val attributeRegistryManagementApi: AttributeApi = AttributeApi(
    ApplicationConfiguration.attributeRegistryManagementURL
  )

  val agreementProcessApi: AgreementApi        = AgreementApi(ApplicationConfiguration.agreementProcessURL)
  val catalogManagementApi: EServiceApi        = EServiceApi(ApplicationConfiguration.catalogManagementURL)
  val tenantManagementApi: TenantManagementApi = TenantManagementApi(ApplicationConfiguration.tenantManagementURL)
  val tenantProcessApi: TenantProcessApi       = TenantProcessApi(ApplicationConfiguration.tenantProcessURL)

  def attributeRegistry(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_]): AttributeRegistryManagementService = AttributeRegistryManagementServiceImpl(
    AttributeRegistryManagementInvoker(blockingEc)(actorSystem.classicSystem),
    attributeRegistryManagementApi
  )

  def agreementProcess(blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_]
  ): AgreementProcessService =
    AgreementProcessServiceImpl(AgreementProcessInvoker(blockingEc)(actorSystem.classicSystem), agreementProcessApi)

  def catalogManagement(blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_]
  ): CatalogManagementService =
    CatalogManagementServiceImpl(CatalogManagementInvoker(blockingEc)(actorSystem.classicSystem), catalogManagementApi)

  def tenantManagement(blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_]
  ): TenantManagementService =
    TenantManagementServiceImpl(TenantManagementInvoker(blockingEc)(actorSystem.classicSystem), tenantManagementApi)

  def tenantProcess(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem[_]): TenantProcessService =
    TenantProcessServiceImpl(TenantProcessInvoker(blockingEc)(actorSystem.classicSystem), tenantProcessApi)

  def userRegistry(implicit actorSystem: ActorSystem[_]): UserRegistryService =
    UserRegistryServiceImpl(
      UserRegistryInvokerInvoker()(actorSystem.classicSystem),
      UserRegistryApi(ApplicationConfiguration.userRegistryURL)
    )

  object UserRegistryInvokerInvoker {
    def apply()(implicit actorSystem: ClassicActorSystem): UserRegistryInvoker =
      userregistry.client.invoker.ApiInvoker(userregistry.client.api.EnumsSerializers.all)
  }

  object UserRegistryApi {
    def apply(baseUrl: String): UserApi = UserApi(baseUrl)
  }

  def getJwtValidator(implicit ec: ExecutionContext): Future[JWTReader] = JWTConfiguration.jwtReader
    .loadKeyset()
    .toFuture
    .map(keyset =>
      new DefaultJWTReader with PublicKeysHolder {
        var publicKeyset: Map[KID, SerializedKey] = keyset

        override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
          getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
      }
    )

  def sessionTokenGenerator(blockingEc: ExecutionContextExecutor)(implicit ec: ExecutionContext) =
    new DefaultSessionTokenGenerator(
      signerService(blockingEc),
      new PrivateKeysKidHolder {
        override val RSAPrivateKeyset: Set[KID] = ApplicationConfiguration.rsaKeysIdentifiers
        override val ECPrivateKeyset: Set[KID]  = ApplicationConfiguration.ecKeysIdentifiers
      }
    )

  private def signerService(blockingEc: ExecutionContextExecutor): SignerService = new KMSSignerService(blockingEc)

  def authorizationApi(jwtReader: JWTReader, blockingEc: ExecutionContextExecutor)(implicit
    ec: ExecutionContext
  ): AuthorizationApi =
    new AuthorizationApi(
      AuthorizationApiServiceImpl(jwtReader, sessionTokenGenerator(blockingEc), rateLimiter),
      AuthorizationApiMarshallerImpl,
      SecurityDirectives.authenticateOAuth2("SecurityRealm", AkkaUtils.PassThroughAuthenticator)
    )

  def partyApi(jwtReader: JWTReader, blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_],
    ec: ExecutionContext
  ): PartyApi =
    new PartyApi(
      PartyApiServiceImpl(partyProcess, userRegistry, attributeRegistry(blockingEc)),
      PartyApiMarshallerImpl,
      jwtReader.OAuth2JWTValidatorAsContexts.flatMap(rateLimiterDirective(ec))
    )

  def attributeApi(jwtReader: JWTReader, blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_],
    ec: ExecutionContext
  ): AttributesApi =
    new AttributesApi(
      AttributesApiServiceImpl(attributeRegistry(blockingEc)),
      AttributesApiMarshallerImpl,
      jwtReader.OAuth2JWTValidatorAsContexts.flatMap(rateLimiterDirective(ec))
    )

  def agreementApi(jwtReader: JWTReader, blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_],
    ec: ExecutionContext
  ): AgreementsApi =
    new AgreementsApi(
      AgreementsApiServiceImpl(
        agreementProcess(blockingEc),
        attributeRegistry(blockingEc),
        catalogManagement(blockingEc),
        partyProcess,
        tenantManagement(blockingEc)
      ),
      AgreementsApiMarshallerImpl,
      jwtReader.OAuth2JWTValidatorAsContexts.flatMap(rateLimiterDirective(ec))
    )

  def tenantApi(jwtReader: JWTReader, blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_],
    ec: ExecutionContext
  ): TenantsApi =
    new TenantsApi(
      TenantsApiServiceImpl(attributeRegistry(blockingEc), tenantManagement(blockingEc), tenantProcess(blockingEc)),
      TenantsApiMarshallerImpl,
      jwtReader.OAuth2JWTValidatorAsContexts.flatMap(rateLimiterDirective(ec))
    )

  val healthApi: HealthApi = new HealthApi(
    new HealthServiceApiImpl(),
    HealthApiMarshallerImpl,
    SecurityDirectives.authenticateOAuth2("SecurityRealm", AkkaUtils.PassThroughAuthenticator),
    loggingEnabled = false
  )

  val validationExceptionToRoute: ValidationReport => Route = report => {
    val error = problemOf(StatusCodes.BadRequest, OpenapiUtils.errorFromRequestValidationReport(report))
    complete(error.status, error)(entityMarshallerProblem)
  }

}
