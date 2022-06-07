package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.{
  MgmtAttribute,
  MgmtAttributesResponse,
  MgmtAttributeSeed
}

import scala.concurrent.Future

trait AttributeRegistryManagementService {
  def getAttributeByOriginAndCode(origin: String, code: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[MgmtAttribute]

  def getAttributes(search: Option[String])(implicit contexts: Seq[(String, String)]): Future[MgmtAttributesResponse]

  def createAttribute(seed: MgmtAttributeSeed)(implicit contexts: Seq[(String, String)]): Future[MgmtAttribute]

}
