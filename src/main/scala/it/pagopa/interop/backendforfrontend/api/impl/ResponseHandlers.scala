package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LoggerTakingImplicit
import it.pagopa.interop.backendforfrontend.error.CatalogProcessErrors.{
  ContentTypeParsingError,
  DescriptorDocumentNotFound
}
import it.pagopa.interop.commons.logging.ContextFieldsToLog
import it.pagopa.interop.commons.utils.errors.{AkkaResponses, ServiceCode}

import scala.util.{Failure, Success, Try}

object ResponseHandlers extends AkkaResponses {

  implicit val serviceCode: ServiceCode = ServiceCode("009")

  def getEServiceDocumentByIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                              => success(s)
      case Failure(ex: DescriptorDocumentNotFound) => notFound(ex, logMessage)
      case Failure(ex: ContentTypeParsingError)    => badRequest(ex, logMessage)
      case Failure(ex)                             => internalServerError(ex, logMessage)
    }

}
