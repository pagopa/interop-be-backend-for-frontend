package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.MgmtAttributesResponse

import scala.concurrent.Future

trait AttributeRegistryManagementService {

  def getAttributes(search: Option[String])(implicit contexts: Seq[(String, String)]): Future[MgmtAttributesResponse]

}
