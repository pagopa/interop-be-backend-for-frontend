package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{onComplete, redirect, complete}
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.jwt.service.SessionTokenGenerator
import it.pagopa.interop.commons.jwt._
import it.pagopa.interop.commons.signer.model.SignatureAlgorithm
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.api.SupportApiService
import it.pagopa.interop.backendforfrontend.service.TenantProcessService
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.tenantprocess.client.{model => TenantProcess}
import it.pagopa.interop.backendforfrontend.error.BFFErrors._
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
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

import cats.syntax.all._
import java.time.{OffsetDateTime, Instant, ZoneOffset}
import java.io.ByteArrayInputStream
import scala.jdk.CollectionConverters._
import scala.concurrent.{Future, ExecutionContext}
import scala.util.Success

class SupportApiServiceImpl(
  sessionTokenGenerator: SessionTokenGenerator,
  tenantProcessService: TenantProcessService,
  offsetDateTimeSupplier: OffsetDateTimeSupplier
)(implicit ec: ExecutionContext)
    extends SupportApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  val SUPPORT_LEVELS: Seq[String] = Seq("L2", "L3")
  val SUPPORT_LEVEL_NAME: String  = "supportLevel"
  val OPERATOR: String            = "OPERATOR"

  override def redirectToSupportPage(
    sAMLResponse: SAMLResponse
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {

    logger.info(s"Calling Support SAML")

    val result: Future[(String, String)] = for {
      tenant       <- ApplicationConfiguration.pagoPaTenantId.toFutureUUID >>= tenantProcessService.getTenant
      selfcareId   <- tenant.selfcareId.toFuture(MissingSelfcareId(tenant.id))
      sessionToken <- sessionTokenGenerator.generate(
        signatureAlgorithm = SignatureAlgorithm.RSAPkcs1Sha256,
        claimsSet = buildClaims(selfcareId, tenant),
        audience = ApplicationConfiguration.generatedJwtAudience,
        tokenIssuer = ApplicationConfiguration.generatedJwtIssuer,
        validityDurationInSeconds = 300
      )
      _            <- validate(parseResponse(sAMLResponse.response))
      base64       <- sAMLResponse.response.encodeBase64.toFuture
    } yield (base64, sessionToken)

    onComplete(result) {
      handleError(s"Error calling support SAML") orElse { case Success((base64, sessionToken)) =>
        val redirectUrl =
          s"https://selfcare.dev.interop.pagopa.it/ui/it/assistenza/scelta-ente#saml2=$base64&jwt=$sessionToken"
        redirect(redirectUrl, StatusCodes.MovedPermanently)
      }
    }
  }

  override def getSaml2Token(tenantId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerSessionToken: ToEntityMarshaller[SessionToken],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Calling get SAML2 token")

    val result: Future[SessionToken] = for {
      tenant       <- ApplicationConfiguration.pagoPaTenantId.toFutureUUID >>= tenantProcessService.getTenant
      selfcareId   <- tenant.selfcareId.toFuture(MissingSelfcareId(tenant.id))
      sessionToken <- sessionTokenGenerator.generate(
        signatureAlgorithm = SignatureAlgorithm.RSAPkcs1Sha256,
        claimsSet = buildClaims(selfcareId, tenant),
        audience = ApplicationConfiguration.generatedJwtAudience,
        tokenIssuer = ApplicationConfiguration.generatedJwtIssuer,
        validityDurationInSeconds = 3600
      )
    } yield SessionToken(sessionToken)

    onComplete(result) {
      handleError(s"Error creating a session token") orElse { case Success(token) =>
        complete(StatusCodes.OK, token)
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

  private def validate(xmlObject: XMLObject): Future[Unit] = {
    val response               = xmlObject.asInstanceOf[Response]
    val sig                    = response.getSignature
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

  private def buildClaims(selfcareId: String, tenant: TenantProcess.Tenant): Map[String, AnyRef] = {
    import spray.json.RootJsonFormat
    import spray.json._

    case class Role(partyRole: String, role: String)
    case class Organization(id: String, name: String, roles: Seq[Role])
    implicit val roleFormat: RootJsonFormat[Role]                 = jsonFormat2(Role)
    implicit val organizationFormat: RootJsonFormat[Organization] = jsonFormat3(Organization)

    Map(
      USER_ROLES            -> SUPPORT_ROLE,
      ORGANIZATION_ID_CLAIM -> tenant.id.toString,
      SELFCARE_ID_CLAIM     -> selfcareId,
      ORGANIZATION_ID_CLAIM -> selfcareId,
      ORGANIZATION          -> Organization(
        id = selfcareId,
        name = tenant.name,
        roles = Seq(Role(partyRole = OPERATOR, role = SUPPORT_ROLE))
      ).toJson.toString,
      UID                   -> SUPPORT_ROLE
    )
  }
}
