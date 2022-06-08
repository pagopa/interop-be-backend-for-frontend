package it.pagopa.interop.backendforfrontend.server.impl.dependencies

import akka.actor.typed.ActorSystem
import akka.actor.{ActorSystem => ClassicActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.SecurityDirectives
import com.atlassian.oai.validator.report.ValidationReport
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.attributeregistrymanagement
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.backendforfrontend.api.impl.{
  AttributesApiMarshallerImpl,
  AttributesApiServiceImpl,
  AuthorizationApiMarshallerImpl,
  AuthorizationApiServiceImpl,
  HealthApiMarshallerImpl,
  HealthServiceApiImpl,
  PartyApiMarshallerImpl,
  PartyApiServiceImpl,
  problemOf
}
import it.pagopa.interop.backendforfrontend.api.{AttributesApi, AuthorizationApi, HealthApi, PartyApi}
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.service.impl.{
  AttributeRegistryManagementServiceImpl,
  PartyProcessServiceImpl,
  UserRegistryServiceImpl
}
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.AttributeRegistryManagementInvoker
import it.pagopa.interop.backendforfrontend.service.types.PartyProcessServiceTypes.{
  PartyProcessApiKeyValue,
  PartyProcessInvoker
}
import it.pagopa.interop.backendforfrontend.service.types.UserRegistryServiceTypes.{
  UserRegistryApiKeyValue,
  UserRegistryInvoker
}
import it.pagopa.interop.backendforfrontend.service.{
  AttributeRegistryManagementService,
  PartyProcessService,
  UserRegistryService
}
import it.pagopa.interop.commons.jwt._
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, DefaultSessionTokenGenerator, getClaimsVerifier}
import it.pagopa.interop.commons.signer.service.SignerService
import it.pagopa.interop.commons.utils.TypeConversions.TryOps
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.commons.utils.{AkkaUtils, OpenapiUtils}
import it.pagopa.interop.commons.signer.service.impl.KMSSignerServiceImpl
import it.pagopa.interop.selfcare.partyprocess.client.api.ProcessApi
import it.pagopa.interop.selfcare.userregistry.client.api.UserApi
import it.pagopa.interop.selfcare.{partyprocess, userregistry}

import scala.concurrent.{ExecutionContext, Future}

trait Dependencies {

  implicit val partyProcessApiKeyValue: PartyProcessApiKeyValue =
    partyprocess.client.invoker.ApiKeyValue(ApplicationConfiguration.partyProcessApiKey)

  implicit val userRegistryApiKeyValue: UserRegistryApiKeyValue =
    userregistry.client.invoker.ApiKeyValue(ApplicationConfiguration.userRegistryApiKey)

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
    def apply()(implicit actorSystem: ClassicActorSystem): AttributeRegistryManagementInvoker =
      attributeregistrymanagement.client.invoker.ApiInvoker(
        attributeregistrymanagement.client.api.EnumsSerializers.all
      )(actorSystem.classicSystem)
  }

  val attributeRegistryManagementApi: AttributeApi = AttributeApi(
    ApplicationConfiguration.attributeRegistryManagementURL
  )

  def attributeRegistry(implicit
    actorSystem: ActorSystem[_],
    executionContext: ExecutionContext
  ): AttributeRegistryManagementService =
    AttributeRegistryManagementServiceImpl(
      AttributeRegistryManagementInvoker()(actorSystem.classicSystem),
      attributeRegistryManagementApi
    )

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

  def sessionTokenGenerator(implicit actorSystem: ActorSystem[_], ec: ExecutionContext) =
    new DefaultSessionTokenGenerator(
      signerService(),
      new PrivateKeysKidHolder {
        override val RSAPrivateKeyset: Set[KID] = ApplicationConfiguration.rsaKeysIdentifiers
        override val ECPrivateKeyset: Set[KID]  = ApplicationConfiguration.ecKeysIdentifiers
      }
    )

  private def signerService()(implicit actorSystem: ActorSystem[_]): SignerService =
    KMSSignerServiceImpl()(actorSystem.classicSystem)

  def authorizationApi(
    jwtReader: JWTReader
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): AuthorizationApi =
    new AuthorizationApi(
      AuthorizationApiServiceImpl(jwtReader, sessionTokenGenerator),
      AuthorizationApiMarshallerImpl,
      SecurityDirectives.authenticateOAuth2("SecurityRealm", AkkaUtils.PassThroughAuthenticator)
    )

  def partyApi(jwtReader: JWTReader)(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): PartyApi =
    new PartyApi(
      PartyApiServiceImpl(partyProcess, userRegistry),
      PartyApiMarshallerImpl,
      jwtReader.OAuth2JWTValidatorAsContexts
    )

  def attributeApi(jwtReader: JWTReader)(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): AttributesApi =
    new AttributesApi(
      AttributesApiServiceImpl(attributeRegistry),
      AttributesApiMarshallerImpl,
      jwtReader.OAuth2JWTValidatorAsContexts
    )

  val healthApi: HealthApi = new HealthApi(
    new HealthServiceApiImpl(),
    HealthApiMarshallerImpl,
    SecurityDirectives.authenticateOAuth2("SecurityRealm", AkkaUtils.PassThroughAuthenticator)
  )

  val validationExceptionToRoute: ValidationReport => Route = report => {
    val error =
      problemOf(
        StatusCodes.BadRequest,
        GenericComponentErrors.ValidationRequestError(OpenapiUtils.errorFromRequestValidationReport(report))
      )
    complete(error.status, error)(HealthApiMarshallerImpl.toEntityMarshallerProblem)
  }

}
