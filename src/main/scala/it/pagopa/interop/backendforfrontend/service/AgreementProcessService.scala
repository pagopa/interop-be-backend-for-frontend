package it.pagopa.interop.backendforfrontend.service

import it.pagopa.interop.agreementprocess.client.model._
import it.pagopa.interop.catalogprocess.client.{model => CatalogProcess}
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait AgreementProcessService {

  def createAgreement(seed: AgreementPayload)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def deleteAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit]
  def getAgreementById(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def activateAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def submitAgreement(agreementId: UUID, payload: AgreementSubmissionPayload)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement]
  def suspendAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def updateAgreement(agreementId: UUID, agreementUpdatePayload: AgreementUpdatePayload)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement]
  def upgradeAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def rejectAgreement(agreementId: UUID, payload: AgreementRejectionPayload)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement]
  def cloneAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]
  def archiveAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]

  def getAgreements(
    producersIds: Seq[UUID] = Seq.empty,
    consumersIds: Seq[UUID] = Seq.empty,
    eservicesIds: Seq[UUID] = Seq.empty,
    descriptorsIds: Seq[UUID] = Seq.empty,
    states: Seq[AgreementState] = Seq.empty,
    limit: Int,
    offset: Int = 0,
    showOnlyUpgradeable: Option[Boolean] = Some(false)
  )(implicit contexts: Seq[(String, String)]): Future[Agreements]

  def addConsumerDocument(agreementId: UUID, seed: DocumentSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Document]

  def getConsumerDocument(agreementId: UUID, documentId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Document]
  def removeConsumerDocument(agreementId: UUID, documentId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]

  def getAgreementEServices(
    eServiceName: Option[String],
    producersIds: Seq[UUID] = Seq.empty,
    consumersIds: Seq[UUID] = Seq.empty,
    limit: Int,
    offset: Int
  )(implicit contexts: Seq[(String, String)]): Future[CompactEServices]

  def getAgreementProducers(producerName: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[CompactOrganizations]

  def getAgreementConsumers(consumerName: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[CompactOrganizations]

  def getAllAgreements(
    producersIds: List[UUID],
    consumersIds: List[UUID],
    eServicesIds: List[UUID],
    states: List[AgreementState]
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[List[Agreement]] = {

    def getAgreementsFrom(offset: Int): Future[List[Agreement]] =
      getAgreements(
        producersIds = producersIds,
        consumersIds = consumersIds,
        eservicesIds = eServicesIds,
        states = states,
        limit = 50,
        offset = offset
      )
        .map(_.results.toList)

    def go(start: Int)(as: List[Agreement]): Future[List[Agreement]] =
      getAgreementsFrom(start).flatMap(agrs =>
        if (agrs.size < 50) Future.successful(as ++ agrs) else go(start + 50)(as ++ agrs)
      )

    go(0)(Nil)
  }

  def getLatestAgreement(consumerId: UUID, eService: CatalogProcess.EService)(implicit
    contexts: Seq[(String, String)],
    ec: ExecutionContext
  ): Future[Option[Agreement]] = {

    val ordering: Ordering[(Int, OffsetDateTime)] =
      Ordering.Tuple2(Ordering.Int.reverse, Ordering.by[OffsetDateTime, Long](_.toEpochSecond).reverse)

    getAllAgreements(
      consumersIds = consumerId :: Nil,
      eServicesIds = eService.id :: Nil,
      producersIds = Nil,
      states = Nil
    ).map(
      _.map(agreement => (agreement, eService.descriptors.find(_.id == agreement.descriptorId)))
        .collect { case (agreement, Some(descriptor)) => (agreement, descriptor) }
        .sortBy(s => (s._2.version.toInt, s._1.createdAt))(ordering)
        .headOption
        .map(_._1)
    )
  }
}
