package it.pagopa.interop.backendforfrontend.error

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

}
