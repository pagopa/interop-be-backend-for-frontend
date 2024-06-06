package it.pagopa.interop.backendforfrontend.common.system

import akka.http.scaladsl.server.directives.FileInfo
import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.error.BFFErrors.InvalidInterfaceContentTypeDetected
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import org.apache.tika.Tika

import java.io.File
import java.nio.file.Files
import scala.util.Try
import it.pagopa.interop.catalogprocess.client.model._

object FileManagerUtils {

  private val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private final val tika: Tika = new Tika()

  def verify(fileParts: (FileInfo, File), eService: EService, isInterface: Boolean)(implicit
    contexts: Seq[(String, String)]
  ): Either[Throwable, Unit] =
    verifyTechnology(fileParts, eService).whenA(isInterface)

  private def verifyTechnology(fileParts: (FileInfo, File), eService: EService)(implicit
    contexts: Seq[(String, String)]
  ): Either[Throwable, Unit] = {
    val restContentTypes: Set[String] = Set("text/x-yaml", "application/x-yaml", "application/json")
    val soapContentTypes: Set[String] = Set("application/xml", "application/soap+xml", "application/wsdl+xml")

    for {
      detectedContentTypes <- Try(tika.detect(Files.readAllBytes(fileParts._2.toPath), fileParts._1.fileName)).toEither
      _ = logger.debug(s"Detected $detectedContentTypes interface content type for eservice: ${eService.id}")
      isValidTechnology = eService.technology match {
        case EServiceTechnology.REST => restContentTypes.contains(detectedContentTypes)
        case EServiceTechnology.SOAP => soapContentTypes.contains(detectedContentTypes)
      }
      _ <- Left(
        InvalidInterfaceContentTypeDetected(eService.id.toString, detectedContentTypes, eService.technology.toString)
      )
        .withRight[Unit]
        .unlessA(isValidTechnology)
    } yield ()
  }
}
