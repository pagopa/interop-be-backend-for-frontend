package it.pagopa.interop.backendforfrontend.error

import it.pagopa.interop.commons.utils.errors.ComponentError

object CatalogProcessErrors {
  final case class ContentTypeParsingError(contentType: String, documentPath: String, errors: List[String])
      extends ComponentError(
        "0001",
        s"Error parsing content type $contentType for document $documentPath. Reasons: ${errors.mkString(",")}"
      )

  final case class DescriptorDocumentNotFound(eServiceId: String, descriptorId: String, documentId: String)
      extends ComponentError(
        "0002",
        s"Error retrieving document $documentId for E-Service $eServiceId and descriptor $descriptorId"
      )
}
