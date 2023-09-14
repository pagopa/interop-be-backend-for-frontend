package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{ContentType, HttpEntity, MediaTypes, HttpHeader, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives.{onComplete, complete}
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.files.service.FileManager
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
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
import it.pagopa.interop.backendforfrontend.common.HeaderUtils._
import cats.syntax.all._

import java.io.File
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class PrivacyNoticesApiServiceImpl(
  consentTypeMap: Map[ConsentType, String],
  privacyNoticesService: PrivacyNoticesService,
  fileManager: FileManager
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
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving privacy notices for consentType $consentType", headers) orElse {
        case Success(res) =>
          getPrivacyNotice200(headers)(res)
      }
    }
  }

  override def acceptPrivacyNotice(consentType: String, seed: PrivacyNoticeSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Accept privacy notices for consentType $consentType")

    val result: Future[Unit] = for {
      userUuid          <- getUidFutureUUID(contexts)
      typeOfConsent     <- ConsentType.fromValue(consentType).toFuture
      privacyNoticeId   <- consentTypeMap.get(typeOfConsent).toFuture(PrivacyNoticeNotFoundInConfiguration(consentType))
      privacyNoticeUuid <- privacyNoticeId.toFutureUUID
      latest            <- privacyNoticesService
        .getLatestVersion(privacyNoticeUuid)
        .flatMap(_.toFuture(PrivacyNoticeNotFound(consentType)))
      _                 <- Future
        .failed(PrivacyNoticeVersionIsNotTheLatest(seed.latestVersionId))
        .unlessA(latest.privacyNoticeVersion.versionId == seed.latestVersionId)
      latestVersionByUser <- privacyNoticesService.getByUserId(privacyNoticeUuid, userUuid)
      _                   <-
        if (latestVersionByUser.exists(value => value.versionNumber == latest.privacyNoticeVersion.version)) Future.unit
        else
          privacyNoticesService.put(
            PersistentModel.UserPrivacyNotice(
              pnIdWithUserId = s"$privacyNoticeUuid#$userUuid",
              versionNumber = latest.privacyNoticeVersion.version,
              privacyNoticeId = privacyNoticeUuid,
              userId = userUuid,
              acceptedAt = OffsetDateTimeSupplier.get(),
              version = PersistentModel.UserPrivacyNoticeVersion(
                versionId = seed.latestVersionId,
                kind = typeOfConsent.toPersistent,
                version = latest.privacyNoticeVersion.version
              )
            )
          )
    } yield ()

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error accepting privacy notices for consentType $consentType", headers) orElse { case Success(_) =>
        acceptPrivacyNotice204(headers)(_)
      }
    }
  }

  override def getPrivacyNoticeContent(consentType: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerFile: ToEntityMarshaller[File]
  ): Route = {
    logger.info(s"Retrieving privacy notices for consentType $consentType")

    val result: Future[HttpEntity.Strict] = for {
      typeOfConsent     <- ConsentType.fromValue(consentType).toFuture
      privacyNoticeId   <- consentTypeMap.get(typeOfConsent).toFuture(PrivacyNoticeNotFoundInConfiguration(consentType))
      privacyNoticeUuid <- privacyNoticeId.toFutureUUID
      latest            <- privacyNoticesService
        .getLatestVersion(privacyNoticeUuid)
        .flatMap(_.toFuture(PrivacyNoticeNotFound(consentType)))
      byteStream        <- fileManager.get(ApplicationConfiguration.privacyNoticesContainer)(
        s"${ApplicationConfiguration.privacyNoticesPath}/${latest.privacyNoticeId.toString}/${latest.privacyNoticeVersion.versionId.toString}/it/${ApplicationConfiguration.privacyNoticesFileName}"
      )
    } yield HttpEntity(ContentType(MediaTypes.`application/json`), byteStream.toByteArray())

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error retrieving privacy notices for consent type $consentType", headers) orElse {
        case Success(privacy) =>
          complete(StatusCodes.OK, headers, privacy)
      }
    }
  }
}
