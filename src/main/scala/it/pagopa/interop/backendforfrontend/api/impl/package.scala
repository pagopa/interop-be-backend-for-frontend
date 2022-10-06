package it.pagopa.interop.backendforfrontend.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCode
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.commons.jwt.JWTConfiguration
import it.pagopa.interop.commons.jwt.service.InteropTokenGenerator
import it.pagopa.interop.commons.utils.{BEARER, UID}
import it.pagopa.interop.commons.utils.TypeConversions.OptionOps
import it.pagopa.interop.commons.utils.SprayCommonFormats.{offsetDateTimeFormat, uuidFormat}
import it.pagopa.interop.commons.utils.errors.ComponentError
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.MissingClaim
import spray.json._

import scala.concurrent.{ExecutionContext, Future}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val tenantFormat: RootJsonFormat[Tenant] = jsonFormat2(Tenant)

  implicit val declaredTenantAttributeSeedFormat: RootJsonFormat[DeclaredTenantAttributeSeed] =
    jsonFormat1(DeclaredTenantAttributeSeed)
  implicit val verifiedTenantAttributeSeedFormat: RootJsonFormat[VerifiedTenantAttributeSeed] =
    jsonFormat3(VerifiedTenantAttributeSeed)

  implicit val declaredTenantAttributeFormat: RootJsonFormat[DeclaredTenantAttribute]   =
    jsonFormat4(DeclaredTenantAttribute)
  implicit val certifiedTenantAttributeFormat: RootJsonFormat[CertifiedTenantAttribute] =
    jsonFormat4(CertifiedTenantAttribute)
  implicit val tenantVerifiedFormat: RootJsonFormat[TenantVerifier]                     = jsonFormat5(TenantVerifier)
  implicit val tenantRevokerFormat: RootJsonFormat[TenantRevoker]                       = jsonFormat6(TenantRevoker)
  implicit val verifiedTenantAttributeFormat: RootJsonFormat[VerifiedTenantAttribute]   =
    jsonFormat5(VerifiedTenantAttribute)
  implicit val tenantAttributeFormat: RootJsonFormat[TenantAttribute]                   = jsonFormat3(TenantAttribute)

  implicit val tenantWithAttributesFormat: RootJsonFormat[TenantWithAttributes] = jsonFormat3(TenantWithAttributes)
  implicit val activeDescriptorFormat: RootJsonFormat[ActiveDescriptor]         = jsonFormat3(ActiveDescriptor)
  implicit val eServiceFormat: RootJsonFormat[EService]                         = jsonFormat4(EService)

  implicit val documentFormat: RootJsonFormat[Document] = jsonFormat5(Document)

  implicit val certifiedAttributeFormat: RootJsonFormat[CertifiedAttribute] = jsonFormat4(CertifiedAttribute)
  implicit val declaredAttributeFormat: RootJsonFormat[DeclaredAttribute]   = jsonFormat4(DeclaredAttribute)
  implicit val verifiedAttributeFormat: RootJsonFormat[VerifiedAttribute]   = jsonFormat4(VerifiedAttribute)

  implicit val agreementFormat: RootJsonFormat[Agreement] = jsonFormat16(Agreement)

  implicit val agreementPayloadFormat: RootJsonFormat[AgreementPayload] = jsonFormat2(AgreementPayload)
  implicit val createdResourceFormat: RootJsonFormat[CreatedResource]   = jsonFormat1(CreatedResource)

  implicit val identityTokenFormat: RootJsonFormat[IdentityToken]       = jsonFormat1(IdentityToken)
  implicit val sessionTokenFormat: RootJsonFormat[SessionToken]         = jsonFormat1(SessionToken)
  implicit val productInfoFormat: RootJsonFormat[ProductInfo]           = jsonFormat3(ProductInfo)
  implicit val relationshipInfoFormat: RootJsonFormat[RelationshipInfo] = jsonFormat11(RelationshipInfo)

  implicit val institutionAttributeFormat: RootJsonFormat[InstitutionAttribute] = jsonFormat3(InstitutionAttribute)
  implicit val institutionFormat: RootJsonFormat[Institution]                   = jsonFormat11(Institution)

  implicit val certifiedAttributesResponseFormat: RootJsonFormat[CertifiedAttributesResponse] = jsonFormat1(
    CertifiedAttributesResponse
  )

  implicit val attributeFormat: RootJsonFormat[Attribute]                   = jsonFormat7(Attribute)
  implicit val attributesResponseFormat: RootJsonFormat[AttributesResponse] = jsonFormat1(AttributesResponse)

  implicit val attributeSeedFormat: RootJsonFormat[AttributeSeed] = jsonFormat5(AttributeSeed)

  implicit val problemErrorFormat: RootJsonFormat[ProblemError] = jsonFormat2(ProblemError)
  implicit val problemFormat: RootJsonFormat[Problem]           = jsonFormat5(Problem)

  final val entityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  final val serviceErrorCodePrefix: String = "016"
  final val defaultProblemType: String     = "about:blank"
  final val defaultErrorMessage: String    = "Unknown error"

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
