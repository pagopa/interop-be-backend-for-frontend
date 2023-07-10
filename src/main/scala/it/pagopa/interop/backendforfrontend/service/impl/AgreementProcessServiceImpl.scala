package it.pagopa.interop.backendforfrontend.service.impl

import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.client.api.{AgreementApi, EnumsSerializers}
import it.pagopa.interop.agreementprocess.client.invoker.{ApiInvoker, BearerToken}
import it.pagopa.interop.agreementprocess.client.model._
import it.pagopa.interop.backendforfrontend.service.AgreementProcessService
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}

class AgreementProcessServiceImpl(agreementProcessURL: String, blockingEc: ExecutionContextExecutor)(implicit
  system: ActorSystem[_]
) extends AgreementProcessService {

  val invoker: ApiInvoker = ApiInvoker(EnumsSerializers.all, blockingEc)(system.classicSystem)
  val api: AgreementApi   = AgreementApi(agreementProcessURL)

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def createAgreement(seed: AgreementPayload)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
    withHeaders[Agreement] { (bearerToken, correlationId, ip) =>
      val request = api.createAgreement(xCorrelationId = correlationId, agreementPayload = seed, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      invoker.invoke(request, s"Creating agreement with seed $seed")
    }

  override def deleteAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Unit] =
    withHeaders[Unit] { (bearerToken, correlationId, ip) =>
      val request =
        api.deleteAgreement(xCorrelationId = correlationId, agreementId = agreementId.toString, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Deleting agreement $agreementId")
    }

  override def getAgreementById(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
    withHeaders[Agreement] { (bearerToken, correlationId, ip) =>
      val request =
        api.getAgreementById(xCorrelationId = correlationId, agreementId = agreementId.toString, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Retrieving agreement $agreementId")
    }

  override def getAgreements(
    producersIds: Seq[UUID],
    consumersIds: Seq[UUID],
    eservicesIds: Seq[UUID],
    descriptorsIds: Seq[UUID],
    states: Seq[AgreementState],
    limit: Int,
    offset: Int,
    showOnlyUpgradeable: Option[Boolean]
  )(implicit contexts: Seq[(String, String)]): Future[Agreements] = withHeaders[Agreements] {
    (bearerToken, correlationId, ip) =>
      val request = api.getAgreements(
        xCorrelationId = correlationId,
        offset = offset,
        limit = limit,
        producersIds = producersIds,
        consumersIds = consumersIds,
        eservicesIds = eservicesIds,
        descriptorsIds = descriptorsIds,
        states = states,
        showOnlyUpgradeable = showOnlyUpgradeable,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      invoker.invoke(request, s"Retrieving agreements")
  }

  override def activateAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
    withHeaders[Agreement] { (bearerToken, correlationId, ip) =>
      val request =
        api.activateAgreement(xCorrelationId = correlationId, agreementId = agreementId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Activating agreement $agreementId")
    }

  override def submitAgreement(agreementId: UUID, payload: AgreementSubmissionPayload)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement] =
    withHeaders[Agreement] { (bearerToken, correlationId, ip) =>
      val request = api.submitAgreement(
        xCorrelationId = correlationId,
        agreementId = agreementId,
        agreementSubmissionPayload = payload,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      invoker.invoke(request, s"Submitting agreement $agreementId")
    }

  override def suspendAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
    withHeaders[Agreement] { (bearerToken, correlationId, ip) =>
      val request = api.suspendAgreement(xCorrelationId = correlationId, agreementId = agreementId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      invoker.invoke(request, s"Suspending agreement $agreementId")
    }

  override def updateAgreement(agreementId: UUID, agreementUpdatePayload: AgreementUpdatePayload)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement] =
    withHeaders[Agreement] { (bearerToken, correlationId, ip) =>
      val request =
        api.updateAgreementById(
          xCorrelationId = correlationId,
          agreementId = agreementId,
          agreementUpdatePayload = agreementUpdatePayload,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, s"Updating agreement $agreementId")
    }

  override def upgradeAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
    withHeaders[Agreement] { (bearerToken, correlationId, ip) =>
      val request =
        api.upgradeAgreementById(xCorrelationId = correlationId, agreementId = agreementId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Upgrading agreement $agreementId")
    }

  override def rejectAgreement(agreementId: UUID, payload: AgreementRejectionPayload)(implicit
    contexts: Seq[(String, String)]
  ): Future[Agreement] =
    withHeaders[Agreement] { (bearerToken, correlationId, ip) =>
      val request = api.rejectAgreement(
        xCorrelationId = correlationId,
        agreementId = agreementId,
        agreementRejectionPayload = payload,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      invoker.invoke(request, s"Rejecting agreement $agreementId")
    }

  override def archiveAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
    withHeaders[Agreement] { (bearerToken, correlationId, ip) =>
      val request = api.archiveAgreement(xCorrelationId = correlationId, agreementId = agreementId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      invoker.invoke(request, s"Archiving agreement $agreementId")
    }

  override def addConsumerDocument(agreementId: UUID, seed: DocumentSeed)(implicit
    contexts: Seq[(String, String)]
  ): Future[Document] = withHeaders[Document] { (bearerToken, correlationId, ip) =>
    val request = api.addAgreementConsumerDocument(
      xCorrelationId = correlationId,
      agreementId = agreementId,
      documentSeed = seed,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    invoker.invoke(request, s"Adding consumer document to agreement $agreementId")
  }

  override def getConsumerDocument(agreementId: UUID, documentId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Document] = withHeaders[Document] { (bearerToken, correlationId, ip) =>
    val request = api.getAgreementConsumerDocument(
      xCorrelationId = correlationId,
      agreementId = agreementId,
      documentId = documentId,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    invoker.invoke(request, s"Getting consumer document $documentId from agreement $agreementId")
  }

  override def removeConsumerDocument(agreementId: UUID, documentId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = withHeaders[Unit] { (bearerToken, correlationId, ip) =>
    val request = api.removeAgreementConsumerDocument(
      xCorrelationId = correlationId,
      agreementId = agreementId,
      documentId = documentId,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    invoker.invoke(request, s"Removing document $documentId from agreement $agreementId")
  }

  override def cloneAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
    withHeaders[Agreement] { (bearerToken, correlationId, ip) =>
      val request = api.cloneAgreement(xCorrelationId = correlationId, agreementId = agreementId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      invoker.invoke(request, s"Cloning agreement $agreementId")
    }

  override def getAgreementEServices(
    eServiceName: Option[String],
    producersIds: Seq[UUID],
    consumersIds: Seq[UUID],
    limit: Int,
    offset: Int
  )(implicit contexts: Seq[(String, String)]): Future[CompactEServices] =
    withHeaders[CompactEServices] { (bearerToken, correlationId, ip) =>
      val request = api.getAgreementEServices(
        xCorrelationId = correlationId,
        eServiceName = eServiceName,
        producersIds = producersIds,
        consumersIds = consumersIds,
        offset = offset,
        limit = limit,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      invoker.invoke(request, s"Retrieving eServices agreement with name $eServiceName")
    }

  override def getAgreementProducers(producerName: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[CompactOrganizations] = withHeaders[CompactOrganizations] { (bearerToken, correlationId, ip) =>
    val request =
      api.getAgreementProducers(
        xCorrelationId = correlationId,
        producerName = producerName,
        offset = offset,
        limit = limit,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(request, s"Retrieving producers from agrements with name $producerName")
  }

  override def getAgreementConsumers(consumerName: Option[String], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[CompactOrganizations] = withHeaders[CompactOrganizations] { (bearerToken, correlationId, ip) =>
    val request =
      api.getAgreementConsumers(
        xCorrelationId = correlationId,
        consumerName = consumerName,
        offset = offset,
        limit = limit,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
    invoker.invoke(request, s"Retrieving consumers from agrements with name $consumerName")
  }
}
