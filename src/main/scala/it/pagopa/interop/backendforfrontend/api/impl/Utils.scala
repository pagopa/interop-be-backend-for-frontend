package it.pagopa.interop.backendforfrontend.api.impl

import cats.syntax.all._
import it.pagopa.interop.agreementprocess.client.{model => AgreementProcess}
import it.pagopa.interop.attributeregistryprocess.client.model.Attribute
import it.pagopa.interop.attributeregistryprocess.client.{model => AttributeRegistry}
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.BFFErrors.SamlNotValid
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service.types.TenantProcessServiceTypes.AdaptableTenantAttribute
import it.pagopa.interop.backendforfrontend.service.types.TenantProcessServiceTypes.AdaptableTenantAttribute._
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}
import it.pagopa.interop.commons.jwt.SUPPORT_ROLE
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

import java.io.ByteArrayInputStream
import java.time.{Instant, OffsetDateTime, ZoneOffset}
import java.util
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory
import scala.jdk.CollectionConverters._
import scala.util.Try
object Utils {

  val PAGOPA = "PagoPA S.p.A."

  def tenantAttributesToApi[DepAttribute, ApiAttribute](
    tenantAttributes: Seq[DepAttribute],
    registryAttributes: Seq[Attribute]
  )(implicit adaptable: AdaptableTenantAttribute[DepAttribute, ApiAttribute]): Seq[ApiAttribute] = {
    val registryMap = registryAttributes.map(a => (a.id, a)).toMap
    tenantAttributes.flatMap(tenantAttributeToApi(_, registryMap))
  }

  def tenantAttributeToApi[DepAttribute, ApiAttribute](
    tenantAttribute: DepAttribute,
    registryAttributesMap: Map[UUID, Attribute]
  )(implicit adaptable: AdaptableTenantAttribute[DepAttribute, ApiAttribute]): Option[ApiAttribute] =
    registryAttributesMap.get(tenantAttribute.id).map(ra => tenantAttribute.toApi(ra.name, ra.description))

  def enhanceTenantAttributes(
    tenantAttributes: Seq[TenantProcess.TenantAttribute],
    registryAttributes: Seq[AttributeRegistry.Attribute]
  ): TenantAttributes = {
    val registryAttributesMap: Map[UUID, Attribute] = registryAttributes.fproductLeft(_.id).toMap

    val declareds: Seq[DeclaredTenantAttribute] = tenantAttributes.collect {
      case TenantProcess.TenantAttribute(Some(declared), None, None) =>
        Utils.tenantAttributeToApi(declared, registryAttributesMap)
    }.flattenOption

    val certifieds: Seq[CertifiedTenantAttribute] = tenantAttributes.collect {
      case TenantProcess.TenantAttribute(None, Some(certified), None) =>
        Utils.tenantAttributeToApi(certified, registryAttributesMap)
    }.flattenOption

    val verifieds: Seq[VerifiedTenantAttribute] = tenantAttributes.collect {
      case TenantProcess.TenantAttribute(None, None, Some(verified)) =>
        Utils.tenantAttributeToApi(verified, registryAttributesMap)
    }.flattenOption

    TenantAttributes(declareds, certifieds, verifieds)
  }

  def tenantAttributesIds(tenant: TenantProcess.Tenant): Seq[UUID] =
    tenant.attributes.mapFilter(_.verified.map(_.id)) ++
      tenant.attributes.mapFilter(_.certified.map(_.id)) ++
      tenant.attributes.mapFilter(_.declared.map(_.id))

  def canBeUpgraded(eService: CatalogProcess.EService, a: AgreementProcess.Agreement): Boolean =
    eService.descriptors.find(_.id == a.descriptorId).exists(isUpgradable(_, eService.descriptors))

  def isUpgradable(
    descriptor: CatalogProcess.EServiceDescriptor,
    descriptors: Seq[CatalogProcess.EServiceDescriptor]
  ): Boolean =
    descriptors
      .filter(_.version.toInt > descriptor.version.toInt)
      .exists(d =>
        d.state == CatalogProcess.EServiceDescriptorState.PUBLISHED ||
          d.state == CatalogProcess.EServiceDescriptorState.SUSPENDED
      )

  def parseResponse(responseXml: String): Try[XMLObject] = Try {
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

  final val SUPPORT_LEVELS: Seq[String]    = Seq("L2", "L3")
  final val SUPPORT_LEVEL_NAME: String     = "supportLevel"
  final val SUPPORT_USER_ID: String        = UUID.fromString("5119b1fa-825a-4297-8c9c-152e055cabca").toString
  final val SELFCARE_OPERATOR_ROLE: String = "OPERATOR"

  def validate(xmlObject: XMLObject)(offsetDateTimeSupplier: OffsetDateTimeSupplier): Either[Throwable, Response] =
    for {
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

  def buildClaims(tenantId: UUID): Map[String, AnyRef] = {

    val role: util.Map[String, AnyRef] = new util.HashMap()
    role.put("partyRole", SELFCARE_OPERATOR_ROLE)
    role.put("role", SUPPORT_ROLE)

    val organization: util.Map[String, AnyRef] = new util.HashMap()
    organization.put("id", tenantId.toString)
    organization.put("name", PAGOPA)
    organization.put("roles", List(role).asJava)

    Map(
      USER_ROLES            -> SUPPORT_ROLE,
      ORGANIZATION_ID_CLAIM -> tenantId.toString,
      ORGANIZATION          -> organization,
      UID                   -> SUPPORT_USER_ID
    )
  }

  def buildClaimsByTenant(selfcareId: String, tenant: TenantProcess.Tenant): Map[String, AnyRef] = {

    val role: util.Map[String, AnyRef] = new util.HashMap()
    role.put("partyRole", SELFCARE_OPERATOR_ROLE)
    role.put("role", SUPPORT_ROLE)

    val organization: util.Map[String, AnyRef] = new util.HashMap()
    organization.put("id", selfcareId)
    organization.put("name", tenant.name)
    organization.put("roles", List(role).asJava)

    Map(
      USER_ROLES            -> SUPPORT_ROLE,
      ORGANIZATION_ID_CLAIM -> tenant.id.toString,
      SELFCARE_ID_CLAIM     -> selfcareId,
      ORGANIZATION          -> organization,
      UID                   -> SUPPORT_USER_ID
    )
  }
}
