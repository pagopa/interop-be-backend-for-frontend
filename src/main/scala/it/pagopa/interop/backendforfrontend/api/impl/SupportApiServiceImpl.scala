package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{onComplete, redirect}
import akka.http.scaladsl.server.Route
import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.SupportApiService
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.BFFErrors._
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service.TenantProcessService
import it.pagopa.interop.backendforfrontend.service.model.JsonFormats._
import it.pagopa.interop.backendforfrontend.service.model.{Organization, Role}
import it.pagopa.interop.commons.jwt._
import it.pagopa.interop.commons.jwt.service.SessionTokenGenerator
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.signer.model.SignatureAlgorithm
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils._
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.interop.tenantprocess.client.{model => TenantProcess}
import org.opensaml.DefaultBootstrap
import org.opensaml.saml2.core.Response
import org.opensaml.security.SAMLSignatureProfileValidator
import org.opensaml.xml.io.{Unmarshaller, UnmarshallerFactory}
import org.opensaml.xml.validation.ValidationException
import org.opensaml.xml.{Configuration, XMLObject}
import org.w3c.dom
import spray.json._

import java.io.ByteArrayInputStream
import java.time.{Instant, OffsetDateTime, ZoneOffset}
import javax.xml.parsers.DocumentBuilderFactory
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

final case class SupportApiServiceImpl(
  sessionTokenGenerator: SessionTokenGenerator,
  tenantProcessService: TenantProcessService,
  offsetDateTimeSupplier: OffsetDateTimeSupplier
)(implicit ec: ExecutionContext)
    extends SupportApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  val SUPPORT_LEVELS: Seq[String]    = Seq("L2", "L3")
  val SUPPORT_LEVEL_NAME: String     = "supportLevel"
  val SELFCARE_OPERATOR_ROLE: String = "OPERATOR"

  override def samlLoginCallback(sAMLResponse: String, relayState: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    logger.info(s"Calling Support SAML")

    val result: Future[(String, String)] = for {
      responseDecoded <- sAMLResponse.decodeBase64.toFuture
      responseXml     <- parseResponse(responseDecoded).toFuture
      _               <- validate(responseXml).toFuture
      tenant          <- tenantProcessService.getTenant(ApplicationConfiguration.pagoPaTenantId)
      selfcareId      <- tenant.selfcareId.toFuture(MissingSelfcareId(tenant.id))
      sessionToken    <- sessionTokenGenerator.generate(
        signatureAlgorithm = SignatureAlgorithm.RSAPkcs1Sha256,
        claimsSet = buildClaims(selfcareId, tenant),
        audience = ApplicationConfiguration.generatedJwtAudience,
        tokenIssuer = ApplicationConfiguration.generatedJwtIssuer,
        validityDurationInSeconds = ApplicationConfiguration.supportLandingJwtDuration
      )
    } yield (sAMLResponse, sessionToken)

    onComplete(result) {
      case Failure(ex)                     => {
        logger.error(s"Error calling support SAML - ${ex.getMessage}")
        val redirectUrl = s"${ApplicationConfiguration.saml2CallbackErrorUrl}"
        redirect(redirectUrl, StatusCodes.Found)
      }
      case Success((base64, sessionToken)) => {
        val redirectUrl = s"${ApplicationConfiguration.saml2CallbackUrl}#saml2=$base64&jwt=$sessionToken"
        redirect(redirectUrl, StatusCodes.Found)
      }
    }
  }

  override def getSaml2Token(sAMLResponse: String, tenantId: String)(implicit
    contexts: Seq[(BearerToken, BearerToken)],
    toEntityMarshallerSessionToken: ToEntityMarshaller[SessionToken],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Calling get SAML2 token")

    val result: Future[SessionToken] = for {
      decodeResponse <- sAMLResponse.decodeBase64.toFuture
      responseXml    <- parseResponse(decodeResponse).toFuture
      _              <- validate(responseXml).toFuture
      tenant         <- tenantId.toFutureUUID.flatMap(tenantProcessService.getTenant)
      selfcareId     <- tenant.selfcareId.toFuture(MissingSelfcareId(tenant.id))
      sessionToken   <- sessionTokenGenerator.generate(
        signatureAlgorithm = SignatureAlgorithm.RSAPkcs1Sha256,
        claimsSet = buildClaims(selfcareId, tenant),
        audience = ApplicationConfiguration.generatedJwtAudience,
        tokenIssuer = ApplicationConfiguration.generatedJwtIssuer,
        validityDurationInSeconds = ApplicationConfiguration.supportJwtDuration
      )
    } yield SessionToken(sessionToken)

    onComplete(result) {
      handleError(s"Error creating a session token") orElse { case Success(token) =>
        getSaml2Token200(token)
      }
    }
  }

  private def parseResponse(responseXml: String): Try[XMLObject] = Try {
    DefaultBootstrap.bootstrap()
    val documentBuilderFactory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
    documentBuilderFactory.setNamespaceAware(true)

    val document: dom.Document                   =
      documentBuilderFactory.newDocumentBuilder().parse(new ByteArrayInputStream(responseXml.getBytes()))
    val element: dom.Element                     = document.getDocumentElement()
    val unmarshallerFactory: UnmarshallerFactory = Configuration.getUnmarshallerFactory()
    val unmarshaller: Unmarshaller               = unmarshallerFactory.getUnmarshaller(element)
    unmarshaller.unmarshall(element)
  }

  private def validate(xmlObject: XMLObject): Either[Throwable, Response] = for {
    response  <- Either
      .catchNonFatal(xmlObject.asInstanceOf[Response])
      .leftMap {
        case e: ClassCastException => SamlNotValid(e.getMessage())
        case e                     => e
      }
    signature <- Option(response.getSignature).toRight(SamlNotValid("Missing Signature"))
    assertions = response.getAssertions().asScala.toList
    _ <- Either.cond(assertions.isEmpty, SamlNotValid("Missing Assertions"), assertions).swap
    audienceRestrictions = assertions.map(_.getConditions()).flatMap(_.getAudienceRestrictions().asScala.toList)
    _ <- Either
      .cond(audienceRestrictions.isEmpty, SamlNotValid("Missing Audience Restricions"), audienceRestrictions)
      .swap
    notBeforeConditions = assertions.map(_.getConditions()).map(_.getNotBefore())
    _ <- Either
      .cond(notBeforeConditions.isEmpty, SamlNotValid("Missing Not Before Restricions"), notBeforeConditions)
      .swap
    notOnOrAfterConditions = assertions.map(_.getConditions()).map(_.getNotOnOrAfter())
    _ <- Either
      .cond(notOnOrAfterConditions.isEmpty, SamlNotValid("Missing On Or After Restricions"), notOnOrAfterConditions)
      .swap
    attributeStatements = assertions.flatMap(_.getAttributeStatements().asScala.toList)
    _ <- Either
      .cond(attributeStatements.isEmpty, SamlNotValid("Missing Attribute Statements"), attributeStatements)
      .swap
    attributes = attributeStatements.flatMap(_.getAttributes().asScala.toList)
    _ <- Either.cond(attributes.isEmpty, SamlNotValid("Missing Attributes"), attributes).swap
    notBefore =
      notBeforeConditions.map(dt => OffsetDateTime.ofInstant(Instant.ofEpochMilli(dt.getMillis()), ZoneOffset.UTC))
    notAfter  =
      notOnOrAfterConditions.map(dt => OffsetDateTime.ofInstant(Instant.ofEpochMilli(dt.getMillis()), ZoneOffset.UTC))
    now       = offsetDateTimeSupplier.get()
    _ <- Either
      .catchNonFatal {
        new SAMLSignatureProfileValidator().validate(
          signature
        ) // Indicates signature did not conform to SAML Signature profile
      }
      .void
      .leftMap {
        case e: ValidationException => SamlNotValid(e.getMessage())
        case e                      => e
      }
    _ <- Either.cond(
      notBefore.exists(nb => now.isAfter(nb)),
      (),
      SamlNotValid("Conditions NotBefore are not compliant")
    )
    _ <- Either.cond(
      notAfter.exists(na => now.isBefore(na) || now.isEqual(na)),
      (),
      SamlNotValid("Conditions NotOnOrAfter are not compliant")
    )
    _ <- attributes
      .find(a =>
        a.getName == SUPPORT_LEVEL_NAME && (a
          .getAttributeValues()
          .asScala
          .toList
          .exists(av => SUPPORT_LEVELS.contains(av.getDOM().getTextContent())))
      )
      .void
      .toRight(SamlNotValid("Support level is not compliant"))
    _ <- Either.cond(
      audienceRestrictions
        .flatMap(_.getAudiences().asScala.toList)
        .exists(aud => ApplicationConfiguration.saml2Audience == aud.getAudienceURI),
      (),
      SamlNotValid("Conditions Audience are not compliant")
    )
  } yield response

  private def buildClaims(selfcareId: String, tenant: TenantProcess.Tenant): Map[String, AnyRef] =
    Map(
      USER_ROLES            -> SUPPORT_ROLE,
      ORGANIZATION_ID_CLAIM -> tenant.id.toString,
      SELFCARE_ID_CLAIM     -> selfcareId,
      ORGANIZATION          -> Organization(
        id = selfcareId,
        name = tenant.name,
        roles = Seq(Role(partyRole = SELFCARE_OPERATOR_ROLE, role = SUPPORT_ROLE))
      ).toJson.asJsObject,
      UID                   -> SUPPORT_ROLE
    )
}
