package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.tenantprocess.client.{model => TenantProcess}

object TenantProcessServiceTypes {

  implicit class DeclaredTenantAttributeSeedConverter(private val seed: DeclaredTenantAttributeSeed) extends AnyVal {
    def toSeed: TenantProcess.DeclaredTenantAttributeSeed = TenantProcess.DeclaredTenantAttributeSeed(id = seed.id)
  }

}
