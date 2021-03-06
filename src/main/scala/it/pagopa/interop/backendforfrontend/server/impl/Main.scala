package it.pagopa.interop.backendforfrontend.server.impl

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.management.scaladsl.AkkaManagement
import buildinfo.BuildInfo
import cats.syntax.all._
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.server.Controller
import it.pagopa.interop.backendforfrontend.server.impl.dependencies.Dependencies
import it.pagopa.interop.commons.logging.renderBuildInfo
import it.pagopa.interop.commons.utils.CORSSupport
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import akka.actor.typed.DispatcherSelector
import scala.concurrent.ExecutionContextExecutor

object Main extends App with CORSSupport with Dependencies {

  private val logger: Logger = Logger(this.getClass)

  ActorSystem[Nothing](
    Behaviors.setup[Nothing] { context =>
      implicit val actorSystem: ActorSystem[_]        = context.system
      implicit val executionContext: ExecutionContext = actorSystem.executionContext

      val selector: DispatcherSelector         = DispatcherSelector.fromConfig("futures-dispatcher")
      val blockingEc: ExecutionContextExecutor = actorSystem.dispatchers.lookup(selector)

      AkkaManagement.get(actorSystem.classicSystem).start()

      logger.info(renderBuildInfo(BuildInfo))

      val serverBinding = for {
        jwtReader <- getJwtValidator
        authorization = authorizationApi(jwtReader, blockingEc)
        party         = partyApi(jwtReader, blockingEc)
        attributes    = attributeApi(jwtReader, blockingEc)
        controller    = new Controller(
          attributes = attributes,
          authorization = authorization,
          party = party,
          health = healthApi,
          validationExceptionToRoute = validationExceptionToRoute.some
        )(actorSystem.classicSystem)
        binding <- Http()(actorSystem.classicSystem)
          .newServerAt("0.0.0.0", ApplicationConfiguration.serverPort)
          .bind(corsHandler(controller.routes))
      } yield binding

      serverBinding.onComplete {
        case Success(b) =>
          logger.info(s"Started server at ${b.localAddress.getHostString}:${b.localAddress.getPort}")
        case Failure(e) =>
          actorSystem.terminate()
          logger.error("Startup error: ", e)
      }

      Behaviors.empty
    },
    BuildInfo.name
  )
}
