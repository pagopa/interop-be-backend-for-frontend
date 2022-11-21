package it.pagopa.interop.backendforfrontend.service.types

import cats.syntax.all._
import it.pagopa.interop.attributeregistrymanagement.client.{model => AttributeManagement}
import it.pagopa.interop.backendforfrontend.error.BFFErrors.AttributeNotExists
import it.pagopa.interop.backendforfrontend.model.EServiceDescriptorState._
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.catalogmanagement.client.{model => CatalogManagement}
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}

import java.util.UUID
import scala.concurrent.Future

object CatalogProcessServiceTypes {

  implicit class EServiceDescriptorStateConverter(private val d: CatalogProcess.EServiceDescriptorState)
      extends AnyVal {
    def toApi: EServiceDescriptorState = d match {
      case CatalogProcess.EServiceDescriptorState.DRAFT      => DRAFT
      case CatalogProcess.EServiceDescriptorState.PUBLISHED  => PUBLISHED
      case CatalogProcess.EServiceDescriptorState.DEPRECATED => DEPRECATED
      case CatalogProcess.EServiceDescriptorState.SUSPENDED  => SUSPENDED
      case CatalogProcess.EServiceDescriptorState.ARCHIVED   => ARCHIVED
    }
  }

  implicit class EServiceDescriptorStateObjectConverter(private val d: CatalogProcess.EServiceDescriptorState.type)
      extends AnyVal {
    def fromApi(s: EServiceDescriptorState): CatalogProcess.EServiceDescriptorState = s match {
      case DRAFT      => CatalogProcess.EServiceDescriptorState.DRAFT
      case PUBLISHED  => CatalogProcess.EServiceDescriptorState.PUBLISHED
      case DEPRECATED => CatalogProcess.EServiceDescriptorState.DEPRECATED
      case SUSPENDED  => CatalogProcess.EServiceDescriptorState.SUSPENDED
      case ARCHIVED   => CatalogProcess.EServiceDescriptorState.ARCHIVED
    }
  }

  implicit class AttributesConverter(private val a: CatalogProcess.Attributes) extends AnyVal {
    def toManagement: CatalogManagement.Attributes =
      CatalogManagement.Attributes(
        certified = a.certified.map(_.toManagement),
        declared = a.declared.map(_.toManagement),
        verified = a.verified.map(_.toManagement)
      )
  }

  implicit class AttributeConverter(private val a: CatalogProcess.Attribute) extends AnyVal {
    def toManagement: CatalogManagement.Attribute =
      CatalogManagement.Attribute(
        single = a.single.map(_.toManagement),
        group = a.group.nested.map(_.toManagement).value
      )
  }

  implicit class AttributeValueConverter(private val a: CatalogProcess.AttributeValue) extends AnyVal {
    def toManagement: CatalogManagement.AttributeValue =
      CatalogManagement.AttributeValue(id = a.id, explicitAttributeVerification = a.explicitAttributeVerification)
  }

  implicit class AgreementApprovalPolicyWrapper(private val aap: CatalogProcess.AgreementApprovalPolicy)
      extends AnyVal {
    def toApi: AgreementApprovalPolicy = aap match {
      case CatalogProcess.AgreementApprovalPolicy.AUTOMATIC => AgreementApprovalPolicy.AUTOMATIC
      case CatalogProcess.AgreementApprovalPolicy.MANUAL    => AgreementApprovalPolicy.MANUAL
    }
  }

  implicit class EServiceDocWrapper(private val esd: CatalogProcess.EServiceDoc) extends AnyVal {
    def toApi: EServiceDoc =
      EServiceDoc(id = esd.id, name = esd.name, contentType = esd.contentType, prettyName = esd.prettyName)
  }

  implicit class EServiceDescriptorWrapper(private val esd: CatalogProcess.EServiceDescriptor) extends AnyVal {

    def toCompactDescriptor: CompactDescriptor = CompactDescriptor(id = esd.id, state = esd.state.toApi, esd.version)
  }
  implicit class EServiceTechnologyWrapper(private val est: CatalogProcess.EServiceTechnology) extends AnyVal {
    def toApi: EServiceTechnology = est match {
      case CatalogProcess.EServiceTechnology.REST => EServiceTechnology.REST
      case CatalogProcess.EServiceTechnology.SOAP => EServiceTechnology.SOAP
    }
  }

  final case class AttributeDetails(name: String, description: String)

  implicit class AttributesWrapper(private val eServiceAttributes: CatalogProcess.Attributes) extends AnyVal {

    def toApi(attributes: Seq[AttributeManagement.Attribute]): Future[EServiceAttributes] = {
      val attributeNames: Map[UUID, AttributeDetails] =
        attributes.map(attr => attr.id -> AttributeDetails(attr.name, attr.description)).toMap

      for {
        certified <- eServiceAttributes.certified.traverse(convertToApiAttribute(attributeNames))
        declared  <- eServiceAttributes.declared.traverse(convertToApiAttribute(attributeNames))
        verified  <- eServiceAttributes.verified.traverse(convertToApiAttribute(attributeNames))
      } yield EServiceAttributes(certified = certified, declared = declared, verified = verified)
    }.toFuture

    private def convertToApiAttribute(
      attributeNames: Map[UUID, AttributeDetails]
    )(attribute: CatalogProcess.Attribute): Either[AttributeNotExists, EServiceAttribute] =
      for {
        single <- attribute.single.traverse(convertToApiAttributeValue(attributeNames))
        group  <- attribute.group.nested.traverse(convertToApiAttributeValue(attributeNames))
      } yield EServiceAttribute(single = single, group = group.value)

    private def convertToApiAttributeValue(
      attributeNames: Map[UUID, AttributeDetails]
    )(value: CatalogProcess.AttributeValue): Either[AttributeNotExists, EServiceAttributeValue] =
      attributeNames
        .get(value.id)
        .toRight(AttributeNotExists(value.id))
        .map(attribute =>
          EServiceAttributeValue(
            id = value.id,
            name = attribute.name,
            description = attribute.description,
            explicitAttributeVerification = value.explicitAttributeVerification
          )
        )
  }

}
