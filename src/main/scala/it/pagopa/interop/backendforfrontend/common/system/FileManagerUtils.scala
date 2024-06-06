package it.pagopa.interop.backendforfrontend.common.system

import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.error.BFFErrors.InvalidInterfaceContentTypeDetected
import it.pagopa.interop.catalogprocess.client.model.{EService, EServiceTechnology}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import org.apache.tika.Tika

import scala.util.Try

object FileManagerUtils {

  private val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private final val tika: Tika = new Tika()

  def verify(docFile: Array[Byte], docName: String, eService: EService, isInterface: Boolean)(implicit
    contexts: Seq[(String, String)]
  ): Either[Throwable, Unit] =
    verifyTechnology(docFile, docName, eService).whenA(isInterface)

  private def verifyTechnology(docFile: Array[Byte], docName: String, eService: EService)(implicit
    contexts: Seq[(String, String)]
  ): Either[Throwable, Unit] = {
    val restContentTypes: Set[String] = Set("text/x-yaml", "application/x-yaml", "application/json")
    val soapContentTypes: Set[String] = Set("application/xml", "application/soap+xml", "application/wsdl+xml")

    for {
      detectedContentTypes <- Try(tika.detect(docFile, docName)).toEither
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
