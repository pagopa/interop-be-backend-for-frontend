package it.pagopa.interop.backendforfrontend.server.impl

import akka.actor.CoordinatedShutdown
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.management.scaladsl.AkkaManagement
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.backendforfrontend.api.{AuthorizationApi, HealthApi}
import it.pagopa.interop.backendforfrontend.server.Controller
import it.pagopa.interop.backendforfrontend.api.impl.{
  AuthorizationApiMarshallerImpl,
  AuthorizationApiServiceImpl,
  HealthApiMarshallerImpl,
  HealthServiceApiImpl,
  problemOf
}
import it.pagopa.interop.backendforfrontend.common.system.{ApplicationConfiguration, classicActorSystem}
import it.pagopa.interop.commons.jwt._
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, DefaultSessionTokenGenerator, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.service.{JWTReader, SessionTokenGenerator}
import it.pagopa.interop.commons.utils.AkkaUtils.PassThroughAuthenticator
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.ValidationRequestError
import it.pagopa.interop.commons.utils.{CORSSupport, OpenapiUtils}
import it.pagopa.interop.commons.vault.service.VaultService
import it.pagopa.interop.commons.vault.service.impl.{DefaultVaultClient, DefaultVaultService}
import kamon.Kamon
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait VaultServiceDependency {
  val vaultService: VaultService = new DefaultVaultService with DefaultVaultClient.DefaultClientInstance
}

//shuts down the actor system in case of startup errors
case object StartupErrorShutdown extends CoordinatedShutdown.Reason

object Main extends App with VaultServiceDependency with CORSSupport {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val dependenciesLoaded: Try[(JWTReader, SessionTokenGenerator)] = for {
    keyset <- JWTConfiguration.jwtReader.loadKeyset()
    jwtValidator          = new DefaultJWTReader with PublicKeysHolder {
      var publicKeyset: Map[KID, SerializedKey]                                        = keyset
      override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
        getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
    }
    sessionTokenGenerator = new DefaultSessionTokenGenerator with PrivateKeysHolder {
      override val RSAPrivateKeyset: Map[KID, SerializedKey] =
        vaultService.readBase64EncodedData(ApplicationConfiguration.rsaPrivatePath)
      override val ECPrivateKeyset: Map[KID, SerializedKey]  =
        Map.empty
    }

  } yield (jwtValidator, sessionTokenGenerator)

  dependenciesLoaded match {
    case Success((jwtValidator, sessionTokenGenerator)) => launchApp(jwtValidator, sessionTokenGenerator)
    case Failure(ex)                                    =>
      logger.error(s"Startup error ${ex.getMessage}")
      logger.error(ex.getStackTrace.mkString("\n"))
      CoordinatedShutdown(classicActorSystem).run(StartupErrorShutdown)
  }

  private def launchApp(
    jwtReader: JWTReader,
    sessionTokenGenerator: SessionTokenGenerator
  ): Future[Http.ServerBinding] = {
    Kamon.init()

    val authorizationApi: AuthorizationApi = new AuthorizationApi(
      AuthorizationApiServiceImpl(jwtReader, sessionTokenGenerator),
      AuthorizationApiMarshallerImpl,
      SecurityDirectives.authenticateOAuth2("SecurityRealm", PassThroughAuthenticator)
    )

    val healthApi: HealthApi = new HealthApi(
      new HealthServiceApiImpl(),
      HealthApiMarshallerImpl,
      SecurityDirectives.authenticateOAuth2("SecurityRealm", PassThroughAuthenticator)
    )

    locally {
      val _ = AkkaManagement.get(classicActorSystem).start()
    }

    val controller: Controller = new Controller(
      health = healthApi,
      authorization = authorizationApi,
      validationExceptionToRoute = Some(report => {
        val error =
          problemOf(
            StatusCodes.BadRequest,
            ValidationRequestError(OpenapiUtils.errorFromRequestValidationReport(report))
          )
        complete(error.status, error)(HealthApiMarshallerImpl.toEntityMarshallerProblem)
      })
    )

    logger.info(s"Started build info = ${buildinfo.BuildInfo.toString}")

    val bindingFuture: Future[Http.ServerBinding] =
      Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(corsHandler(controller.routes))
    bindingFuture
  }
}
