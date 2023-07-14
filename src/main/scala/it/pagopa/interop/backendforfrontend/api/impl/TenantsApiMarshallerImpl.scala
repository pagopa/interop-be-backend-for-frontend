package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.backendforfrontend.api.TenantsApiMarshaller
import it.pagopa.interop.backendforfrontend.model._
import spray.json.DefaultJsonProtocol

object TenantsApiMarshallerImpl extends TenantsApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def toEntityMarshallerCertifiedAttributesResponse: ToEntityMarshaller[CertifiedAttributesResponse] =
    sprayJsonMarshaller[CertifiedAttributesResponse]

  override implicit def fromEntityUnmarshallerDeclaredTenantAttributeSeed
    : FromEntityUnmarshaller[DeclaredTenantAttributeSeed] = sprayJsonUnmarshaller[DeclaredTenantAttributeSeed]

  override implicit def fromEntityUnmarshallerVerifiedTenantAttributeSeed
    : FromEntityUnmarshaller[VerifiedTenantAttributeSeed] = sprayJsonUnmarshaller[VerifiedTenantAttributeSeed]

  override implicit def toEntityMarshallerVerifiedAttributesResponse: ToEntityMarshaller[VerifiedAttributesResponse] =
    sprayJsonMarshaller[VerifiedAttributesResponse]

  override implicit def toEntityMarshallerDeclaredAttributesResponse: ToEntityMarshaller[DeclaredAttributesResponse] =
    sprayJsonMarshaller[DeclaredAttributesResponse]

  override implicit def fromEntityUnmarshallerTenantDelta: FromEntityUnmarshaller[TenantDelta] =
    sprayJsonUnmarshaller[TenantDelta]

  override implicit def toEntityMarshallerCompactOrganizations: ToEntityMarshaller[CompactOrganizations] =
    sprayJsonMarshaller[CompactOrganizations]

  override implicit def toEntityMarshallerTenant: ToEntityMarshaller[Tenant] =
    sprayJsonMarshaller[Tenant]

  override implicit def fromEntityUnmarshallerUpdateVerifiedTenantAttributeSeed
    : FromEntityUnmarshaller[UpdateVerifiedTenantAttributeSeed] =
    sprayJsonUnmarshaller[UpdateVerifiedTenantAttributeSeed]

  override implicit def toEntityMarshallerTenants: ToEntityMarshaller[Tenants] = sprayJsonMarshaller[Tenants]
}
