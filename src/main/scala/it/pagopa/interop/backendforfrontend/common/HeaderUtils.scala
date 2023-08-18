package it.pagopa.interop.backendforfrontend.common

import it.pagopa.interop.commons.utils.AkkaUtils._

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.RawHeader
import it.pagopa.interop.commons.utils.CONTENT_LANGUAGE

object HeaderUtils {
  def headersFromContext()(implicit contexts: Seq[(String, String)]): List[HttpHeader] = {
    List(getAcceptLanguage(contexts).toOption.map(RawHeader(CONTENT_LANGUAGE, _))).flatten
  }
}
