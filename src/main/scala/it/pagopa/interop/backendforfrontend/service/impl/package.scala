package it.pagopa.interop.backendforfrontend.service

import scala.concurrent.Future
import it.pagopa.interop.commons.utils.extractHeaders

package object impl {
  def withHeaders[T](
    f: (String, String, Option[String]) => Future[T]
  )(implicit contexts: Seq[(String, String)]): Future[T] = extractHeaders(contexts) match {
    case Left(ex) => Future.failed(ex)
    case Right(x) => f.tupled(x)
  }
}
