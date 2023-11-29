package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.selfcare.v2.client.model._
import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID

trait SelfcareV2ClientService {

  def getInstitutionProductUsers(institutionId: UUID, requesterId: UUID, userId: Option[UUID], roles: Seq[String])(
    implicit contexts: Seq[(String, String)]
  ): Future[Seq[UserResource]]

  def getInstitutionUserProducts(institutionId: UUID, userId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Seq[ProductResource]]

  def getInstitutions(userId: UUID)(implicit contexts: Seq[(String, String)]): Future[Seq[InstitutionResource]]

  def getInstitution(
    selfcareId: UUID
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution]

  def getUserById(selfcareId: UUID, userId: UUID)(implicit
    contexts: Seq[(String, String)],
    ec: ExecutionContext
  ): Future[UserResponse]

  def getOnboardingsInstitution(institutionId: UUID, productId: Option[String])(implicit
    contexts: Seq[(String, String)]
  ): Future[OnboardingsResponse]
}
