package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.selfcare.v2.client.model._
import scala.concurrent.Future
import java.util.UUID

trait SelfcareClientService {

  def getInstitutionUserProducts(userId: UUID, institutionId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Seq[ProductResource]]

  def getInstitutions(userId: UUID)(implicit contexts: Seq[(String, String)]): Future[Seq[InstitutionResource]]

  def getInstitution(id: UUID)(implicit contexts: Seq[(String, String)]): Future[Institution]
}
