package it.pagopa.interop.backendforfrontend.server.impl.dependencies

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.http.scaladsl.server.{Directive1, Route}
import cats.implicits._
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
  ClientsApiMarshallerImpl,
  ClientsApiServiceImpl,
  EServicesApiMarshallerImpl,
  EServicesApiServiceImpl,
  HealthApiMarshallerImpl,
  HealthServiceApiImpl,
  PartyApiMarshallerImpl,
  PartyApiServiceImpl,
  PurposesApiMarshallerImpl,
  PurposesApiServiceImpl,
  SelfcareApiMarshallerImpl,
  SelfcareApiServiceImpl,
  TenantsApiMarshallerImpl,
  TenantsApiServiceImpl,
  ToolsApiMarshallerImpl,
  ToolsApiServiceImpl,
  entityMarshallerProblem,
  problemOf,
  serviceErrorCodePrefix
}
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.server.Controller
import it.pagopa.interop.backendforfrontend.service._
import it.pagopa.interop.backendforfrontend.service.impl._
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.commons.jwt._
import it.pagopa.interop.commons.jwt.service.impl.{
  DefaultInteropTokenGenerator,
  DefaultJWTReader,
  DefaultSessionTokenGenerator,
  getClaimsVerifier
}
import it.pagopa.interop.commons.jwt.service.{JWTReader, SessionTokenGenerator}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.ratelimiter.RateLimiter
import it.pagopa.interop.commons.ratelimiter.akkahttp.RateLimiterDirective
import it.pagopa.interop.commons.ratelimiter.impl.RedisRateLimiter
import it.pagopa.interop.commons.signer.service.SignerService
import it.pagopa.interop.commons.signer.service.impl.KMSSignerService
import it.pagopa.interop.commons.utils.TypeConversions.TryOps
import it.pagopa.interop.commons.utils.errors.ServiceCode
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.commons.utils.{AkkaUtils, OpenapiUtils}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait Dependencies {

  implicit val loggerTI: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog]("OAuth2JWTValidatorAsContexts")

  private val rateLimiter: RateLimiter =
    RedisRateLimiter(ApplicationConfiguration.rateLimiterConfigs, OffsetDateTimeSupplier)

  private val rateLimiterDirective: ExecutionContext => Seq[(String, String)] => Directive1[Seq[(String, String)]] = {
    val logger: LoggerTakingImplicit[ContextFieldsToLog] = Logger.takingImplicit[ContextFieldsToLog](this.getClass)
    ec =>
      contexts => {
        RateLimiterDirective.rateLimiterDirective(rateLimiter)(contexts)(
          ec,
          ServiceCode(serviceErrorCodePrefix),
          logger
        )
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

  def fileManager(blockingEc: ExecutionContextExecutor): FileManager =
    FileManager.get(ApplicationConfiguration.storageKind match {
      case "S3"   => FileManager.S3
      case "file" => FileManager.File
      case _      => throw new Exception("Incorrect File Manager")
    })(blockingEc)

  def getAllowList(blockingEc: ExecutionContextExecutor): Future[List[String]] = {
    val filePath: String = s"${ApplicationConfiguration.allowListPath}/${ApplicationConfiguration.allowListFilename}"
    fileManager(blockingEc)
      .get(ApplicationConfiguration.allowListContainer)(filePath)
      .map(byteStream => new String(byteStream.toByteArray).split('\n').flatMap(_.split(',')).toList)(blockingEc)
  }

  def makeController(jwtReader: JWTReader, allowList: List[String], blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_]
  ): Controller = {

    implicit val ec: ExecutionContext = actorSystem.executionContext

    val oauthAndRateLimitingDirective: Directive1[Seq[(String, String)]] =
      jwtReader.OAuth2JWTValidatorAsContexts.flatMap(rateLimiterDirective(ec))

    val partyProcess: PartyProcessService                               =
      new PartyProcessServiceImpl(ApplicationConfiguration.partyProcessURL, ApplicationConfiguration.partyProcessApiKey)
    val attributeRegistryProcess: AttributeRegistryProcessService       =
      new AttributeRegistryProcessServiceImpl(ApplicationConfiguration.attributeRegistryProcessURL, blockingEc)
    val attributeRegistryManagement: AttributeRegistryManagementService =
      new AttributeRegistryManagementServiceImpl(ApplicationConfiguration.attributeRegistryManagementURL, blockingEc)
    val agreementProcess: AgreementProcessService                       =
      new AgreementProcessServiceImpl(ApplicationConfiguration.agreementProcessURL, blockingEc)
    val catalogProcess: CatalogProcessService                           =
      new CatalogProcessServiceImpl(ApplicationConfiguration.catalogProcessURL, blockingEc)
    val tenantManagement: TenantManagementService                       =
      new TenantManagementServiceImpl(ApplicationConfiguration.tenantManagementURL, blockingEc)
    val userRegistry: UserRegistryService                               =
      new UserRegistryServiceImpl(ApplicationConfiguration.userRegistryURL, ApplicationConfiguration.userRegistryApiKey)
    val tenantProcess: TenantProcessService                             =
      new TenantProcessServiceImpl(ApplicationConfiguration.tenantProcessURL, blockingEc)
    val purposeProcess: PurposeProcessService                           =
      new PurposeProcessServiceImpl(ApplicationConfiguration.purposeProcessURL, blockingEc)
    val authorizationManagement: AuthorizationManagementService         =
      new AuthorizationManagementServiceImpl(ApplicationConfiguration.authorizationManagementURL, blockingEc)
    val authorizationProcess: AuthorizationProcessService               =
      new AuthorizationProcessServiceImpl(ApplicationConfiguration.authorizationProcessURL, blockingEc)
    val selfcareClient: SelfcareClientService                           =
      new SelfcareClientServiceImpl(ApplicationConfiguration.selfcareV2URL, ApplicationConfiguration.selfcareV2ApiKey)

    val signerService: SignerService = new KMSSignerService(blockingEc)

    val sessionTokenGenerator: SessionTokenGenerator = new DefaultSessionTokenGenerator(
      signerService,
      new PrivateKeysKidHolder {
        override val RSAPrivateKeyset: Set[KID] = ApplicationConfiguration.rsaKeysIdentifiers
        override val ECPrivateKeyset: Set[KID]  = ApplicationConfiguration.ecKeysIdentifiers
      }
    )

    val interopTokenGenerator: DefaultInteropTokenGenerator = new DefaultInteropTokenGenerator(
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
        interopTokenGenerator,
        tenantManagement,
        tenantProcess,
        partyProcess,
        allowList,
        rateLimiter
      ),
      AuthorizationApiMarshallerImpl,
      SecurityDirectives.authenticateOAuth2("SecurityRealm", AkkaUtils.PassThroughAuthenticator)
    )

    val partyApi: PartyApi = new PartyApi(
      PartyApiServiceImpl(partyProcess, userRegistry, attributeRegistryManagement, tenantProcess),
      PartyApiMarshallerImpl,
      oauthAndRateLimitingDirective
    )

    val attributesApi: AttributesApi = new AttributesApi(
      AttributesApiServiceImpl(attributeRegistryManagement, attributeRegistryProcess),
      AttributesApiMarshallerImpl,
      oauthAndRateLimitingDirective
    )

    val agreementsApi: AgreementsApi = new AgreementsApi(
      AgreementsApiServiceImpl(
        agreementProcess,
        attributeRegistryManagement,
        catalogProcess,
        partyProcess,
        tenantProcess,
        fileManager(blockingEc),
        UUIDSupplier
      ),
      AgreementsApiMarshallerImpl,
      oauthAndRateLimitingDirective
    )

    val tenantsApi: TenantsApi = new TenantsApi(
      TenantsApiServiceImpl(attributeRegistryManagement, tenantProcess),
      TenantsApiMarshallerImpl,
      oauthAndRateLimitingDirective
    )

    val eServicesApi: EservicesApi = new EservicesApi(
      EServicesApiServiceImpl(
        agreementProcess,
        attributeRegistryManagement,
        catalogProcess,
        tenantProcess,
        partyProcess,
        fileManager(blockingEc),
        UUIDSupplier
      ),
      EServicesApiMarshallerImpl,
      oauthAndRateLimitingDirective
    )

    val purposesApi: PurposesApi = new PurposesApi(
      PurposesApiServiceImpl(
        catalogProcess,
        purposeProcess,
        tenantProcess,
        agreementProcess,
        authorizationProcess,
        fileManager(blockingEc)
      ),
      PurposesApiMarshallerImpl,
      oauthAndRateLimitingDirective
    )

    val clientsApi: ClientsApi =
      new ClientsApi(
        ClientsApiServiceImpl(authorizationProcess, tenantProcess, catalogProcess, purposeProcess, partyProcess),
        ClientsApiMarshallerImpl,
        oauthAndRateLimitingDirective
      )

    val selfcareApi: SelfcareApi =
      new SelfcareApi(
        SelfcareApiServiceImpl(selfcareClient, tenantProcess),
        SelfcareApiMarshallerImpl,
        oauthAndRateLimitingDirective
      )

    val toolsApi: ToolsApi =
      new ToolsApi(
        ToolsApiServiceImpl(authorizationManagement, agreementProcess, catalogProcess, purposeProcess),
        ToolsApiMarshallerImpl,
        oauthAndRateLimitingDirective
      )

    new Controller(
      attributes = attributesApi,
      authorization = authorizationApi,
      agreements = agreementsApi,
      selfcare = selfcareApi,
      tenants = tenantsApi,
      eservices = eServicesApi,
      purposes = purposesApi,
      clients = clientsApi,
      party = partyApi,
      tools = toolsApi,
      health = healthApi,
      validationExceptionToRoute = validationExceptionToRoute.some
    )(actorSystem.classicSystem)
  }

}
