package it.pagopa.interop.backendforfrontend.error

import akka.http.scaladsl.model.ErrorInfo
import it.pagopa.interop.commons.utils.errors.ComponentError

import java.util.UUID

object BFFErrors {

  final case class RelationshipNotFound(relationshipId: String)
      extends ComponentError("0002", s"Relationship $relationshipId not found")

  final case class MissingUserFields(userId: String, missingUserFields: String)
      extends ComponentError("0003", s"Missing some fields for user $userId - $missingUserFields")

  final case class InstitutionNotFound(institutionId: String)
      extends ComponentError("0004", s"Institution $institutionId not found")

  final case class AgreementDescriptorNotFound(agreementId: UUID)
      extends ComponentError("0005", s"Descriptor of agreement $agreementId not found")

  final case class MissingSelfcareId(tenantId: UUID)
      extends ComponentError("0006", s"SelfcareId in Tenant ${tenantId.toString()} not found")

  final case class ContractNotFound(agreementId: String)
      extends ComponentError("0007", s"Contract not found for agreement $agreementId")

  final case class EServiceDescriptorNotFound(eServiceId: String, descriptorId: String)
      extends ComponentError("0008", s"Descriptor ${descriptorId} not found in Eservice ${eServiceId}")

  final case class InvalidContentType(
    contentType: String,
    agreementId: String,
    documentId: String,
    errors: List[ErrorInfo]
  ) extends ComponentError(
        "0008",
        s"Invalid contentType $contentType for document $documentId from agreement $agreementId - ${errors.map(_.detail).mkString(",")}"
      )

  final case class AttributeNotExists(id: UUID)
      extends ComponentError("0009", s"Attribute ${id.toString} does not exist in the attribute registry")
}
