package it.pagopa.interop.backendforfrontend.server.impl

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.management.scaladsl.AkkaManagement
import buildinfo.BuildInfo
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.server.impl.dependencies.Dependencies
import it.pagopa.interop.commons.logging.renderBuildInfo
import it.pagopa.interop.commons.utils.CORSSupport

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.{Failure, Success}

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
        allowList  <- getAllowList(blockingEc)
        controller <- getJwtValidator.map(makeController(_, allowList, blockingEc))
        binding    <- Http()(actorSystem.classicSystem)
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
