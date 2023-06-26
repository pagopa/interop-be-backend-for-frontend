package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives.onComplete
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.PrivacyNoticesApiService
import it.pagopa.interop.backendforfrontend.service.types.PrivacyNoticesServiceTypes._
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service._
import it.pagopa.interop.backendforfrontend.service.{model => PersistentModel}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.error.BFFErrors._
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.AkkaUtils._
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import cats.syntax.all._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class PrivacyNoticesApiServiceImpl(
  consentTypeMap: Map[ConsentType, String],
  privacyNoticesService: PrivacyNoticesService
)(implicit ec: ExecutionContext)
    extends PrivacyNoticesApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getPrivacyNotice(consentType: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPrivacyNotice: ToEntityMarshaller[PrivacyNotice],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Retrieving privacy notices for consentType $consentType")

    val result: Future[PrivacyNotice] = for {
      userUuid <- getUidFutureUUID(contexts)
      ctype    <- ConsentType.fromValue(consentType).toFuture
      ppId     <- consentTypeMap.get(ctype).toFuture(PrivacyNoticeNotFoundInConfiguration(consentType))
      ppUuid   <- ppId.toFutureUUID
      latest   <- privacyNoticesService.getLatestVersion(ppUuid).flatMap(_.toFuture(PrivacyNoticeNotFound(consentType)))
      userPrivacyNotice <- privacyNoticesService.getByUserId(ppUuid, userUuid)
    } yield userPrivacyNotice.fold(
      PrivacyNotice(
        id = ppUuid,
        userId = userUuid,
        consentType = ctype,
        firstAccept = false,
        isUpdated = false,
        latestVersionId = latest.privacyNoticeVersion.versionId
      )
    )(upn =>
      upn.toApi(
        firstAccept = true,
        isUpdated = (latest.privacyNoticeVersion.version == upn.version.version),
        latestVersionId = latest.privacyNoticeVersion.versionId
      )
    )

    onComplete(result) {
      handleError(s"Error retrieving privacy notices for consentType $consentType") orElse { case Success(res) =>
        getPrivacyNotice200(res)
      }
    }
  }

  override def acceptPrivacyNotice(consentType: String, seed: PrivacyNoticeSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Accept privacy notices for consentType $consentType")

    val result: Future[Unit] = for {
      userUuid <- getUidFutureUUID(contexts)
      ctype    <- ConsentType.fromValue(consentType).toFuture
      pnId     <- consentTypeMap.get(ctype).toFuture(PrivacyNoticeNotFoundInConfiguration(consentType))
      pnUuid   <- pnId.toFutureUUID
      latest   <- privacyNoticesService.getLatestVersion(pnUuid).flatMap(_.toFuture(PrivacyNoticeNotFound(consentType)))
      _        <- Future
        .failed(PrivacyNoticeVersionIsNotTheLatest(seed.latestVersionId))
        .unlessA(latest.privacyNoticeVersion.versionId == seed.latestVersionId)
      _        <- privacyNoticesService.put(
        PersistentModel.UserPrivacyNotice(
          pnIdWithUserId = s"$pnUuid#$userUuid",
          versionNumber = latest.privacyNoticeVersion.version,
          privacyNoticeId = pnUuid,
          userId = userUuid,
          acceptedAt = OffsetDateTimeSupplier.get(),
          version = PersistentModel.UserPrivacyNoticeVersion(
            versionId = seed.latestVersionId,
            kind = ctype.toPersistent,
            version = latest.privacyNoticeVersion.version
          )
        )
      )
    } yield ()

    onComplete(result) {
      handleError(s"Error accepting privacy notices for consentType $consentType") orElse { case Success(_) =>
        acceptPrivacyNotice204(_)
      }
    }
  }
}
