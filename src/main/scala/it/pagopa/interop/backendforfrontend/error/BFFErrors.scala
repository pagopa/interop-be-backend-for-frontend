package it.pagopa.interop.backendforfrontend.error

import akka.http.scaladsl.model.ErrorInfo
import it.pagopa.interop.backendforfrontend.model.TokenGenerationValidationResult
import it.pagopa.interop.clientassertionvalidation.Errors.ClientAssertionValidationError
import it.pagopa.interop.commons.ratelimiter.model.RateLimitStatus
import it.pagopa.interop.commons.utils.errors.ComponentError

import java.util.UUID

object BFFErrors {

  final case class RelationshipNotFound(relationshipId: String)
      extends ComponentError("0001", s"Relationship $relationshipId not found")

  final case class MissingUserFields(userId: String, missingUserFields: String)
      extends ComponentError("0002", s"Missing some fields for user $userId - $missingUserFields")

  final case class AgreementDescriptorNotFound(agreementId: UUID)
      extends ComponentError("0003", s"Descriptor of agreement $agreementId not found")

  final case class MissingSelfcareId(tenantId: UUID)
      extends ComponentError("0004", s"SelfcareId in Tenant ${tenantId.toString()} not found")

  final case class ContractNotFound(agreementId: String)
      extends ComponentError("0005", s"Contract not found for agreement $agreementId")

  final case class EServiceDescriptorNotFound(eServiceId: String, descriptorId: String)
      extends ComponentError("0006", s"Descriptor ${descriptorId} not found in Eservice ${eServiceId}")

  final case class InvalidContentType(
    contentType: String,
    agreementId: String,
    documentId: String,
    errors: List[ErrorInfo]
  ) extends ComponentError(
        "0007",
        s"Invalid contentType $contentType for document $documentId from agreement $agreementId - ${errors.map(_.detail).mkString(",")}"
      )

  final case class AttributeNotExists(id: UUID)
      extends ComponentError("0008", s"Attribute ${id.toString} does not exist in the attribute registry")

  final case class InvalidEServiceRequester(eServiceId: UUID, requesterId: UUID)
      extends ComponentError(
        "0009",
        s"EService ${eServiceId.toString} does not belong to producer ${requesterId.toString}"
      )

  final case class SessionTokenTooManyRequests(tenantId: UUID, rateLimitStatus: RateLimitStatus)
      extends ComponentError("0010", s"Too many requests on Session Token requests for tenant $tenantId")

  final case class DownstreamError(errorCode: String, message: String) extends ComponentError(errorCode, message)

  final case class UnknownTenantOrigin(selfcareId: String)
      extends ComponentError("0011", s"SelfcareID ${selfcareId} is not inside whitelist or related with IPA")

  final case class EServiceNotFound(eServiceId: UUID) extends ComponentError("0012", s"EService $eServiceId not found")

  final case class TenantNotFound(tenantId: UUID) extends ComponentError("0013", s"Tenant $tenantId not found")

  final case class ContentTypeParsingError(contentType: String, documentPath: String, errors: List[String])
      extends ComponentError(
        "0014",
        s"Error parsing content type $contentType for document $documentPath. Reasons: ${errors.mkString(",")}"
      )

  final case class InvalidInterfaceContentTypeDetected(eServiceId: String, contentType: String, technology: String)
      extends ComponentError(
        "0015",
        s"The interface file for EService $eServiceId has a contentType $contentType not admitted for $technology technology"
      )

  final case class NoDescriptorInEservice(eServiceId: UUID)
      extends ComponentError("0016", s"No descriptor found in eService $eServiceId")

  final case class InvalidInterfaceFileDetected(eServiceId: String)
      extends ComponentError("0017", s"The interface file for EService $eServiceId is invalid")

  final case class InvalidRiskAnalysisContentType(
    contentType: String,
    purposeId: String,
    versionId: String,
    documentId: String,
    errors: List[ErrorInfo]
  ) extends ComponentError(
        "0018",
        s"Invalid contentType $contentType for document $documentId from purpose $purposeId and version $versionId - ${errors.map(_.detail).mkString(",")}"
      )

  final case class PurposeVersionDraftNotFound(purposeId: UUID)
      extends ComponentError("0019", s"Version in DRAFT state for Purpose $purposeId not found")

  final case class AgreementNotFound(consumerId: UUID)
      extends ComponentError("0020", s"Agreement of consumer $consumerId not found")

  final case class PurposeNotFound(purposeId: UUID) extends ComponentError("0021", s"Purpose $purposeId not found")

  final case class KidNotFound(clientId: UUID, kid: String)
      extends ComponentError("0022", s"Kid $kid not found in Client $clientId")

  final case class OrganizationNotAllowed(clientId: UUID)
      extends ComponentError("0023", s"Organization not allowed for Client $clientId")

  final case class ClientAssertionValidationWrapper(result: TokenGenerationValidationResult)
      extends ComponentError("0024", "Validation results")

  final case class ClientAssertionPublicKeyNotFound(kid: String, clientId: UUID)
      extends ClientAssertionValidationError("8099", s"Public key with kid $kid not found for client $clientId")

  final case class PrivacyNoticeNotFoundInConfiguration(privacyNoticeKind: String)
      extends ComponentError("0025", s"PrivacyNotice $privacyNoticeKind not found in configuration")

  final case class PrivacyNoticeNotFound(privacyNoticeKind: String)
      extends ComponentError("0026", s"PrivacyNotice $privacyNoticeKind not found")

  final case class PrivacyNoticeVersionIsNotTheLatest(versionId: UUID)
      extends ComponentError("0027", s"PrivacyNotice version $versionId not found")

  final case class DynamoReadingError(message: String)
      extends ComponentError("0028", s"Error while reading data from Dynamo -> $message")

  final case class SamlNotValid(message: String)
      extends ComponentError("0029", s"Error while validating saml -> $message")

  final case class SelfcareNotFound(selfcare: UUID)
      extends ComponentError("0030", s"Tenant with selfcare $selfcare not found")

  final case class EServiceRiskAnalysisNotFound(eServiceId: UUID, riskAnalysisId: UUID)
      extends ComponentError("0031", s"RiskAnalysis ${riskAnalysisId} not found in Eservice ${eServiceId}")

}
