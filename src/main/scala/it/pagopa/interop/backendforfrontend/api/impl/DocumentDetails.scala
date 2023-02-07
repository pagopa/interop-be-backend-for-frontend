package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.model.ContentType

import java.io.ByteArrayOutputStream

final case class DocumentDetails(name: String, contentType: ContentType, data: ByteArrayOutputStream)
