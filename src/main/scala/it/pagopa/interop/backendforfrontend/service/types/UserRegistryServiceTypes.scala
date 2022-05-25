package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.selfcare.userregistry

object UserRegistryServiceTypes {
  type UserRegistryInvoker     = userregistry.client.invoker.ApiInvoker
  type UserRegistryApiKeyValue = userregistry.client.invoker.ApiKeyValue
}
