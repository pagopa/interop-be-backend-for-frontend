package it.pagopa.interop.backendforfrontend.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.client.api.AgreementApi
import it.pagopa.interop.agreementprocess.client.invoker.BearerToken
import it.pagopa.interop.agreementprocess.client.model.{Agreement, AgreementPayload, AgreementState}
import it.pagopa.interop.backendforfrontend.service.AgreementProcessService
import it.pagopa.interop.backendforfrontend.service.types.AttributeRegistryServiceTypes.AgreementProcessInvoker
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import java.util.UUID
import scala.concurrent.Future

final case class AgreementProcessServiceImpl(invoker: AgreementProcessInvoker, api: AgreementApi)
    extends AgreementProcessService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def createAgreement(seed: AgreementPayload)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
    withHeaders[Agreement] { (bearerToken, correlationId, ip) =>
      val request = api.createAgreement(xCorrelationId = correlationId, agreementPayload = seed, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      invoker.invoke(request, s"Creating agreement with seed $seed")
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
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eServiceId: Option[String] = None,
    descriptorId: Option[String] = None,
    states: Seq[AgreementState],
    latest: Option[Boolean] = None
  )(implicit contexts: Seq[(String, String)]): Future[Seq[Agreement]] = withHeaders[Seq[Agreement]] {
    (bearerToken, correlationId, ip) =>
      val request = api.getAgreements(
        xCorrelationId = correlationId,
        producerId = producerId,
        consumerId = consumerId,
        eserviceId = eServiceId,
        descriptorId = descriptorId,
        states = states,
        latest = latest,
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

  override def submitAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
    withHeaders[Agreement] { (bearerToken, correlationId, ip) =>
      val request = api.submitAgreement(xCorrelationId = correlationId, agreementId = agreementId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      invoker.invoke(request, s"Submitting agreement $agreementId")
    }

  override def suspendAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
    withHeaders[Agreement] { (bearerToken, correlationId, ip) =>
      val request = api.suspendAgreement(xCorrelationId = correlationId, agreementId = agreementId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      invoker.invoke(request, s"Suspending agreement $agreementId")
    }

  override def upgradeAgreement(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement] =
    withHeaders[Agreement] { (bearerToken, correlationId, ip) =>
      val request =
        api.upgradeAgreementById(xCorrelationId = correlationId, agreementId = agreementId, xForwardedFor = ip)(
          BearerToken(bearerToken)
        )
      invoker.invoke(request, s"Upgrading agreement $agreementId")
    }

}
