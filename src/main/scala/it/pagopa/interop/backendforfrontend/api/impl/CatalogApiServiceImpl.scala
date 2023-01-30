package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.CatalogApiService
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model.{EServiceSeed, OldEService, Problem}
import it.pagopa.interop.backendforfrontend.server.impl.Main.loggerTI.canLogEv
import it.pagopa.interop.backendforfrontend.service.CatalogProcessService
import it.pagopa.interop.backendforfrontend.service.types.CatalogProcessServiceTypes.CatalogEServiceSeedConverter
import it.pagopa.interop.backendforfrontend.service.types.CatalogProcessServiceTypes.CatalogOldEServiceConverter
import it.pagopa.interop.commons.logging.ContextFieldsToLog

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class CatalogApiServiceImpl(catalogProcess: CatalogProcessService)(implicit ec: ExecutionContext)
    extends CatalogApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def createEService(eServiceSeed: EServiceSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerOldEService: ToEntityMarshaller[OldEService],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[OldEService] =
      catalogProcess.createEService(eServiceSeed.toProcess)(contexts).map(_.toApi)

    onComplete(result) {
      handleError(s"Error creating eservice with seed: $eServiceSeed") orElse { case Success(eservice) =>
        createEService200(eservice)
      }
    }
  }
}
