package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.authorizationprocess.client.{model => AuthorizationProcess}
import it.pagopa.interop.backendforfrontend.model._

object AuthorizationProcessServiceTypes {

  implicit class ClientConverter(private val client: AuthorizationProcess.Client) extends AnyVal {
    def toApi: CompactClient =
      CompactClient(id = client.id, name = client.name)
  }
}
