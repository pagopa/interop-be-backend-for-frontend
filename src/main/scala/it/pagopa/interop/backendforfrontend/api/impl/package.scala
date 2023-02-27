package it.pagopa.interop.backendforfrontend.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCode
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.commons.jwt.JWTConfiguration
import it.pagopa.interop.commons.jwt.service.InteropTokenGenerator
import it.pagopa.interop.commons.utils.SprayCommonFormats.{offsetDateTimeFormat, uuidFormat}
import it.pagopa.interop.commons.utils.TypeConversions.OptionOps
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.MissingClaim
import it.pagopa.interop.commons.utils.errors.{ComponentError, ServiceCode}
import it.pagopa.interop.commons.utils.{BEARER, UID}
import spray.json._

import scala.concurrent.{ExecutionContext, Future}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val tenantDelta: RootJsonFormat[TenantDelta] = jsonFormat2(TenantDelta)

  implicit val tenantFormat: RootJsonFormat[CompactTenant] = jsonFormat2(CompactTenant)

  implicit val paginationFormat: RootJsonFormat[Pagination] = jsonFormat3(Pagination)

  implicit val compactOrganizationFormat: RootJsonFormat[CompactOrganization]       = jsonFormat2(CompactOrganization)
  implicit val compactEServiceFormat: RootJsonFormat[CompactEService]               = jsonFormat3(CompactEService)
  implicit val compactAgreementEServiceFormat: RootJsonFormat[CompactEServiceLight] = jsonFormat2(CompactEServiceLight)
  implicit val compactAgreementEServicesFormat: RootJsonFormat[CompactEServicesLight] = jsonFormat2(
    CompactEServicesLight
  )
  implicit val compactOrganizationsFormat: RootJsonFormat[CompactOrganizations] = jsonFormat2(CompactOrganizations)
  implicit val compactAgreementFormat: RootJsonFormat[CompactAgreement]         = jsonFormat2(CompactAgreement)
  implicit val compactDescriptorFormat: RootJsonFormat[CompactDescriptor]       = jsonFormat3(CompactDescriptor)
  implicit val catalogEServiceFormat: RootJsonFormat[CatalogEService]           = jsonFormat8(CatalogEService)
  implicit val catalogEServicesFormat: RootJsonFormat[CatalogEServices]         = jsonFormat2(CatalogEServices)
  implicit val producerEServiceFormat: RootJsonFormat[ProducerEService]         = jsonFormat4(ProducerEService)
  implicit val producerEServicesFormat: RootJsonFormat[ProducerEServices]       = jsonFormat2(ProducerEServices)

  implicit val declaredTenantAttributeSeedFormat: RootJsonFormat[DeclaredTenantAttributeSeed] =
    jsonFormat1(DeclaredTenantAttributeSeed)
  implicit val verifiedTenantAttributeSeedFormat: RootJsonFormat[VerifiedTenantAttributeSeed] =
    jsonFormat3(VerifiedTenantAttributeSeed)

  implicit val declaredTenantAttributeFormat: RootJsonFormat[DeclaredTenantAttribute]   =
    jsonFormat5(DeclaredTenantAttribute)
  implicit val certifiedTenantAttributeFormat: RootJsonFormat[CertifiedTenantAttribute] =
    jsonFormat5(CertifiedTenantAttribute)
  implicit val tenantVerifiedFormat: RootJsonFormat[TenantVerifier]                     = jsonFormat5(TenantVerifier)
  implicit val tenantRevokerFormat: RootJsonFormat[TenantRevoker]                       = jsonFormat6(TenantRevoker)
  implicit val verifiedTenantAttributeFormat: RootJsonFormat[VerifiedTenantAttribute]   =
    jsonFormat6(VerifiedTenantAttribute)
  implicit val tenantAttributesFormat: RootJsonFormat[TenantAttributes]                 =
    jsonFormat3(TenantAttributes)

  implicit val agreementsEServiceFormat: RootJsonFormat[AgreementsEService] = jsonFormat4(AgreementsEService)

  implicit val documentFormat: RootJsonFormat[Document] = jsonFormat5(Document)

  implicit val certifiedAttributeFormat: RootJsonFormat[CertifiedAttribute] = jsonFormat4(CertifiedAttribute)
  implicit val declaredAttributeFormat: RootJsonFormat[DeclaredAttribute]   = jsonFormat4(DeclaredAttribute)
  implicit val verifiedAttributeFormat: RootJsonFormat[VerifiedAttribute]   = jsonFormat4(VerifiedAttribute)

  implicit val agreementRejectionPayloadFormat: RootJsonFormat[AgreementRejectionPayload] =
    jsonFormat1(AgreementRejectionPayload)
  implicit val agreementPayloadFormat: RootJsonFormat[AgreementPayload] = jsonFormat2(AgreementPayload)
  implicit val agreementSubmissionPayloadFormat: RootJsonFormat[AgreementSubmissionPayload] =
    jsonFormat1(AgreementSubmissionPayload)
  implicit val agreementUpdatePayloadFormat: RootJsonFormat[AgreementUpdatePayload]         =
    jsonFormat1(AgreementUpdatePayload)
  implicit val createdResourceFormat: RootJsonFormat[CreatedResource]                     = jsonFormat1(CreatedResource)
  implicit val CreatedEServiceDescriptorFormat: RootJsonFormat[CreatedEServiceDescriptor] = jsonFormat2(
    CreatedEServiceDescriptor
  )

  implicit val identityTokenFormat: RootJsonFormat[IdentityToken]       = jsonFormat1(IdentityToken)
  implicit val sessionTokenFormat: RootJsonFormat[SessionToken]         = jsonFormat1(SessionToken)
  implicit val productInfoFormat: RootJsonFormat[ProductInfo]           = jsonFormat3(ProductInfo)
  implicit val relationshipInfoFormat: RootJsonFormat[RelationshipInfo] = jsonFormat11(RelationshipInfo)

  implicit val externalIdFormat: RootJsonFormat[ExternalId]                 = jsonFormat2(ExternalId)
  implicit val mailFormat: RootJsonFormat[Mail]                             = jsonFormat2(Mail)
  implicit val institutionFormat: RootJsonFormat[Tenant]                    = jsonFormat8(Tenant)
  implicit val agreementFormat: RootJsonFormat[Agreement]                   = jsonFormat18(Agreement)
  implicit val agreementListEntryFormat: RootJsonFormat[AgreementListEntry] = jsonFormat9(AgreementListEntry)
  implicit val agreementsFormat: RootJsonFormat[Agreements]                 = jsonFormat2(Agreements)

  implicit val certifiedAttributesResponseFormat: RootJsonFormat[CertifiedAttributesResponse] =
    jsonFormat1(CertifiedAttributesResponse)
  implicit val declaredAttributesResponseFormat: RootJsonFormat[DeclaredAttributesResponse]   =
    jsonFormat1(DeclaredAttributesResponse)
  implicit val verifiedAttributesResponseFormat: RootJsonFormat[VerifiedAttributesResponse]   =
    jsonFormat1(VerifiedAttributesResponse)

  implicit val attributeFormat: RootJsonFormat[Attribute]                   = jsonFormat7(Attribute)
  implicit val attributesResponseFormat: RootJsonFormat[AttributesResponse] = jsonFormat1(AttributesResponse)

  implicit val attributeSeedFormat: RootJsonFormat[AttributeSeed] = jsonFormat5(AttributeSeed)

  implicit val eServiceDocFormat: RootJsonFormat[EServiceDoc]                       = jsonFormat4(EServiceDoc)
  implicit val eServiceAttributeValueFormat: RootJsonFormat[EServiceAttributeValue] =
    jsonFormat4(EServiceAttributeValue)
  implicit val eServiceAttributeFormat: RootJsonFormat[EServiceAttribute]           = jsonFormat2(EServiceAttribute)
  implicit val eServiceAttributesFormat: RootJsonFormat[EServiceAttributes]         = jsonFormat3(EServiceAttributes)
  implicit val updateEServiceSeed: RootJsonFormat[UpdateEServiceSeed]               = jsonFormat4(UpdateEServiceSeed)
  implicit val catalogDescriptorEServiceFormat: RootJsonFormat[CatalogDescriptorEService] =
    jsonFormat12(CatalogDescriptorEService)
  implicit val catalogEServiceDescriptorFormat: RootJsonFormat[CatalogEServiceDescriptor] =
    jsonFormat12(CatalogEServiceDescriptor)

  implicit val producerDescriptorEService: RootJsonFormat[ProducerDescriptorEService] =
    jsonFormat8(ProducerDescriptorEService)

  implicit val producerEServiceDescriptorFormat: RootJsonFormat[ProducerEServiceDescriptor] =
    jsonFormat12(ProducerEServiceDescriptor)

  implicit val producerEServiceDetailsFormat: RootJsonFormat[ProducerEServiceDetails] =
    jsonFormat5(ProducerEServiceDetails)

  implicit val compactPurposeVersionFormat: RootJsonFormat[CompactPurposeVersion]   = jsonFormat4(CompactPurposeVersion)
  implicit val PurposeVersionResourceFormat: RootJsonFormat[PurposeVersionResource] = jsonFormat2(
    PurposeVersionResource
  )
  implicit val purposeFormat: RootJsonFormat[Purpose]                               = jsonFormat8(Purpose)
  implicit val purposesFormat: RootJsonFormat[Purposes]                             = jsonFormat2(Purposes)

  implicit val EServiceSeedFormat: RootJsonFormat[EServiceSeed]                           = jsonFormat4(EServiceSeed)
  implicit val updateEServiceDescriptorSeed: RootJsonFormat[UpdateEServiceDescriptorSeed] = jsonFormat6(
    UpdateEServiceDescriptorSeed
  )
  implicit val problemErrorFormat: RootJsonFormat[ProblemError]                           = jsonFormat2(ProblemError)
  implicit val problemFormat: RootJsonFormat[Problem]                                     = jsonFormat6(Problem)

  implicit val EServiceDescriptorSeedFormat: RootJsonFormat[EServiceDescriptorSeed] = jsonFormat6(
    EServiceDescriptorSeed
  )
  implicit val UpdateEServiceDescriptorDocumentSeedFormat: RootJsonFormat[UpdateEServiceDescriptorDocumentSeed] =
    jsonFormat1(UpdateEServiceDescriptorDocumentSeed)

  final val entityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  final val serviceErrorCodePrefix: String    = "016"
  final implicit val serviceCode: ServiceCode = ServiceCode(serviceErrorCodePrefix)
  final val defaultProblemType: String        = "about:blank"
  final val defaultErrorMessage: String       = "Unknown error"

  def problemOf(httpError: StatusCode, error: ComponentError): Problem =
    Problem(
      `type` = defaultProblemType,
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors = Seq(
        ProblemError(
          code = s"$serviceErrorCodePrefix-${error.code}",
          detail = Option(error.getMessage).getOrElse(defaultErrorMessage)
        )
      )
    )

  def problemOf(httpError: StatusCode, errors: List[ComponentError]): Problem =
    Problem(
      `type` = defaultProblemType,
      status = httpError.intValue,
      title = httpError.defaultMessage,
      correlationId = None,
      errors = errors.map(error =>
        ProblemError(
          code = s"$serviceErrorCodePrefix-${error.code}",
          detail = Option(error.getMessage).getOrElse(defaultErrorMessage)
        )
      )
    )

  def generateInternalTokenContexts(tokenGenerator: InteropTokenGenerator, sessionClaims: Map[String, AnyRef])(implicit
    ec: ExecutionContext,
    contexts: Seq[(String, String)]
  ): Future[Seq[(String, String)]] = for {
    uid      <- getUid(sessionClaims)
    m2mToken <- tokenGenerator
      .generateInternalToken(
        subject = JWTConfiguration.jwtInternalTokenConfig.subject,
        audience = JWTConfiguration.jwtInternalTokenConfig.audience.toList,
        tokenIssuer = JWTConfiguration.jwtInternalTokenConfig.issuer,
        secondsDuration = JWTConfiguration.jwtInternalTokenConfig.durationInSeconds
      )
  } yield contexts ++ Seq((BEARER, m2mToken.serialized), (UID, uid))

  private def getUid(sessionClaims: Map[String, AnyRef]): Future[String] =
    sessionClaims.get(UID).map(_.toString).toFuture(MissingClaim("uid in selfcare token"))

}
