package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.authorizationprocess.client.{model => AuthorizationProcess}
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier

object AuthorizationProcessServiceTypes {

  implicit class CLientConverter(private val client: AuthorizationProcess.Client) extends AnyVal {
    def toApi: ClientEntry =
      ClientEntry(id = client.id, name = client.name, creationDate = OffsetDateTimeSupplier.get())
  }
}
