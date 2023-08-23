package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.attributeregistryprocess.client.model.{
  Attribute,
  Attributes,
  AttributeKind,
  AttributeSeed,
  CertifiedAttributeSeed
}

import scala.concurrent.{Future, ExecutionContext}
import java.util.UUID

trait AttributeRegistryProcessService {
  def getAttributes(q: Option[String], origin: Option[String], limit: Int, offset: Int, kinds: Seq[AttributeKind])(
    implicit contexts: Seq[(String, String)]
  ): Future[Attributes]

  def getAttributeByOriginAndCode(origin: String, code: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Attribute]

  def getAttributeById(id: UUID)(implicit contexts: Seq[(String, String)]): Future[Attribute]

  def getBulkAttributes(
    ids: Seq[UUID]
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Seq[Attribute]]

  def getBulkAttributes(requestBody: Seq[UUID], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[Attributes]

  def createDeclaredAttribute(seed: AttributeSeed)(implicit contexts: Seq[(String, String)]): Future[Attribute]
  def createVerifiedAttribute(seed: AttributeSeed)(implicit contexts: Seq[(String, String)]): Future[Attribute]
  def createCertifiedAttribute(seed: CertifiedAttributeSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Attribute]
}
