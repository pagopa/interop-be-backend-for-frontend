package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.authorizationmanagement.client.{model => AuthorizationManagement}
import it.pagopa.interop.backendforfrontend.model._

object AuthorizationManagementServiceTypes {

  implicit class ClientKindConverter(private val ck: AuthorizationManagement.ClientKind) extends AnyVal {
    def toApi: ClientKind = ck match {
      case AuthorizationManagement.ClientKind.API      => ClientKind.API
      case AuthorizationManagement.ClientKind.CONSUMER => ClientKind.CONSUMER
    }
  }
}
