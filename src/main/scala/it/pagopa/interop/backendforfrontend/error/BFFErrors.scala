package it.pagopa.interop.backendforfrontend.error

import it.pagopa.interop.commons.utils.errors.ComponentError

object BFFErrors {

  final case object CreateSessionTokenRequestError
      extends ComponentError("0001", s"Error while creating a session token for this request")

  final case class RelationshipNotFound(relationshipId: String)
      extends ComponentError("0002", s"Relationship $relationshipId not found")

}
