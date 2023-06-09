package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Route
// import akka.http.scaladsl.model.StatusCodes
// import akka.http.scaladsl.server.Directives.{onComplete, redirect}
import akka.http.scaladsl.server.Directives.onComplete
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.jwt.service.SessionTokenGenerator
import it.pagopa.interop.commons.jwt._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.commons.signer.model.SignatureAlgorithm
import it.pagopa.interop.backendforfrontend.api.SupportApiService
// import it.pagopa.interop.backendforfrontend.service.TenantProcessService
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.error.BFFErrors._
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.utils.service.UUIDSupplier
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils._
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom
import org.opensaml.saml2.core.Response
import org.opensaml.security.SAMLSignatureProfileValidator
import org.opensaml.xml.validation.ValidationException
import org.opensaml.xml.{XMLObject, Configuration}
import org.opensaml.DefaultBootstrap
import org.opensaml.xml.io.{Unmarshaller, UnmarshallerFactory}
// import org.opensaml.xml.security.credential.Credential
// import java.security.cert.CertificateFactory
// import java.security.cert.{X509Certificate => JavaX509Certificate}
// import org.opensaml.xml.security.x509.BasicX509Credential
// import org.opensaml.xml.signature.SignatureValidator

import cats.syntax.all._
import java.time.{OffsetDateTime, Instant, ZoneOffset}
import java.io.ByteArrayInputStream
import scala.jdk.CollectionConverters._
import scala.concurrent.{Future, ExecutionContext}
import java.util.UUID
import scala.util.Success

class SupportApiServiceImpl(
  jwtReader: JWTReader,
  sessionTokenGenerator: SessionTokenGenerator,
  // tenantProcessService: TenantProcessService,
  uuidSupplier: UUIDSupplier,
  offsetDateTimeSupplier: OffsetDateTimeSupplier
)(implicit ec: ExecutionContext)
    extends SupportApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  val ORGANIZATION_ID_CLAIM: String    = "organization.id"
  val ORGANIZATION_NAME_CLAIM: String  = "organization.name"
  val ORGANIZATION_ROLES_CLAIM: String = "organization.roles"
  val SUPPORT_LEVELS: Seq[String]      = Seq("L2", "L3")
  val SUPPORT_LEVEL_NAME: String       = "supportLevel"

  override def redirectToSupportPage(sAMLResponse: SAMLResponse)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerTestSAML: ToEntityMarshaller[TestSAML],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    logger.info(s"Calling Support SAML")

    // val result: Future[(String, String)] = for {
    val result: Future[TestSAML] = for {
      // tenant     <- ApplicationConfiguration.pagoPaTenantId.toFutureUUID >>= tenantProcessService.getTenant
      // selfcareId <- tenant.selfcareId.toFuture(MissingSelfcareId(tenant.id))
      selfcareUuId <- uuidSupplier.get().toString().toFutureUUID
      claims = buildClaims(selfcareUuId, "PagoPa") // tenant)
      sessionToken <- sessionTokenGenerator.generate(
        signatureAlgorithm = SignatureAlgorithm.RSAPkcs1Sha256,
        claimsSet = claims,
        audience = ApplicationConfiguration.generatedJwtAudience,
        tokenIssuer = ApplicationConfiguration.generatedJwtIssuer,
        validityDurationInSeconds = 300
      )
      _            <- validate(parseResponse(sAMLResponse.response))
      claims       <- jwtReader.getClaims(sessionToken).toFuture
      _ = logger.info(s"CLAIMS ${claims}")
      base64 <- sAMLResponse.response.encodeBase64.toFuture
    } yield TestSAML(base64 = base64, token = sessionToken) // yield (base64, sessionToken)

    onComplete(result) {
      handleError(s"Error calling support SAML") orElse { case Success(resource) =>
        redirectToSupportPage200(resource)
      // case Success((base64, sessionToken)) =>
      // val redirectUrl =
      //   s"https://selfcare.dev.interop.pagopa.it/ui/it/assistenza/scelta-ente#saml2=$base64&jwt=$sessionToken"
      // redirect(redirectUrl, StatusCodes.MovedPermanently)
      }
    }
  }

  private def parseResponse(responseXml: String): XMLObject = {
    DefaultBootstrap.bootstrap()
    val documentBuilderFactory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance();
    documentBuilderFactory.setNamespaceAware(true);

    val document: dom.Document                   =
      documentBuilderFactory.newDocumentBuilder().parse(new ByteArrayInputStream(responseXml.getBytes()));
    val element: dom.Element                     = document.getDocumentElement();
    val unmarshallerFactory: UnmarshallerFactory = Configuration.getUnmarshallerFactory()
    val unmarshaller: Unmarshaller               = unmarshallerFactory.getUnmarshaller(element)
    unmarshaller.unmarshall(element)
  }

  // private def getCredential(x509Certificate: String): Credential = {
  //   logger.info(s"Certificate X509 SAML ${x509Certificate}")

  //   val cf: CertificateFactory              = CertificateFactory.getInstance("X509")
  //   val cert: JavaX509Certificate           =
  //     cf.generateCertificate(new ByteArrayInputStream(x509Certificate.trim.getBytes))
  //       .asInstanceOf[JavaX509Certificate]
  //   val x509Credential: BasicX509Credential = new BasicX509Credential()
  //   x509Credential.setPublicKey(cert.getPublicKey())
  //   x509Credential.setEntityCertificate(cert)
  //   x509Credential
  // }

  private def validate(xmlObject: XMLObject): Future[Unit] = {

    val response               = xmlObject.asInstanceOf[Response]
    val sig                    = response.getSignature
    // val x509Data               = sig.getKeyInfo().getX509Datas().asScala.toList
    // val cacerts                = x509Data.flatMap(_.getX509Certificates().asScala.toList).map(_.getValue())
    val assertions             = response.getAssertions().asScala.toList
    val audienceRestriction    = assertions.map(_.getConditions()).flatMap(_.getAudienceRestrictions().asScala.toList)
    val notBeforeConditions    = assertions.map(_.getConditions()).map(_.getNotBefore)
    val notOnOrAfterConditions = assertions.map(_.getConditions()).map(_.getNotOnOrAfter())
    val attributeStatements    = assertions.flatMap(_.getAttributeStatements().asScala.toList)
    val attrs                  = attributeStatements.flatMap(_.getAttributes().asScala.toList)
    val notBefore: List[OffsetDateTime] =
      notBeforeConditions.map(dt => OffsetDateTime.ofInstant(Instant.ofEpochMilli(dt.getMillis()), ZoneOffset.UTC))
    val notAfter: List[OffsetDateTime]  =
      notOnOrAfterConditions.map(dt => OffsetDateTime.ofInstant(Instant.ofEpochMilli(dt.getMillis()), ZoneOffset.UTC))

    val now = offsetDateTimeSupplier.get()

    for {
      _ <- Either
        .catchNonFatal {
          new SAMLSignatureProfileValidator().validate(
            sig
          ) // Indicates signature did not conform to SAML Signature profile
        }
        .void
        .leftMap {
          case e: ValidationException => SamlNotValid(e.getMessage())
          case e                      => e
        }
        .toFuture
      // x509Certificate <- cacerts.headOption.toFuture(SamlNotValid("Missing X509 Certificates"))
      // _               <- Either
      //   .catchNonFatal {
      //     new SignatureValidator(
      //       getCredential(s"""-----BEGIN CERTIFICATE-----\n${x509Certificate}\n-----END CERTIFICATE-----""")
      //     ).validate(sig) // Indicates signature was not cryptographically valid, or possibly a processing error.
      //   }
      //   .void
      //   .leftMap {
      //     case e: ValidationException => SamlNotValid(e.getMessage())
      //     case e                      => e
      //   }
      //   .toFuture
      _ <- Future
        .failed(SamlNotValid("Conditions NotBefore are not compliant"))
        .whenA(notBefore.exists(nb => now.isBefore(nb)))
      _ <- Future
        .failed(SamlNotValid("Conditions NotOnOrAfter are not compliant"))
        .whenA(notAfter.exists(na => now.isAfter(na)))
      _ <- attrs
        .find(a =>
          a.getName == SUPPORT_LEVEL_NAME && (a
            .getAttributeValues()
            .asScala
            .toList
            .exists(av => SUPPORT_LEVELS.contains(av.getDOM().getTextContent())))
        )
        .toFuture(SamlNotValid("Support level is not compliant"))
      _ <- Future
        .failed(SamlNotValid("Conditions Audience are not compliant"))
        .unlessA(
          audienceRestriction
            .flatMap(_.getAudiences().asScala.toList)
            .exists(aud => ApplicationConfiguration.generatedJwtAudience.contains(aud.getDOM().getTextContent()))
        )
    } yield ()
  }

  private def buildClaims(selfcareId: UUID, name: String): Map[String, AnyRef] = { // tenant: Tenant)
    Map(
      USER_ROLES               -> SUPPORT_ROLE,
      ORGANIZATION_ID_CLAIM    -> ApplicationConfiguration.pagoPaTenantId, // tenant.id.toString,
      SELFCARE_ID_CLAIM        -> selfcareId.toString,
      ORGANIZATION_ID_CLAIM    -> selfcareId.toString,
      ORGANIZATION_NAME_CLAIM  -> name,                                    // tenant.name,
      ORGANIZATION_ROLES_CLAIM -> s"""[{"partyRole": "OPERATOR", "role": "$SUPPORT_ROLE" }]""",
      UID                      -> SUPPORT_ROLE
    )
  }
}
