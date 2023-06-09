package it.pagopa.interop.backendforfrontend.service.impl

import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import org.scanamo.DynamoReadError.describe
import org.scanamo._
import org.scanamo.syntax._
import org.scanamo.ops.ScanamoOps
import it.pagopa.interop.backendforfrontend.service.PrivacyNoticesService
import it.pagopa.interop.backendforfrontend.error.BFFErrors.DynamoReadingError
import it.pagopa.interop.backendforfrontend.service.model.{PrivacyNotice, UserPrivacyNotice}

import java.util.UUID
import scala.concurrent.{Future, ExecutionContext}

class PrivacyNoticesServiceImpl(privacyNoticesTableName: String, privacyNoticesUsersTableName: String)(implicit
  ec: ExecutionContext,
  scanamo: ScanamoAsync
) extends PrivacyNoticesService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  val pnTable: Table[PrivacyNotice] =
    Table[PrivacyNotice](privacyNoticesTableName)

  val userTable: Table[UserPrivacyNotice] =
    Table[UserPrivacyNotice](privacyNoticesUsersTableName)

  override def getLatestVersion(id: UUID)(implicit contexts: Seq[(String, String)]): Future[Option[PrivacyNotice]] = {
    logger.info(s"Getting id $id privacy notice")
    scanamo
      .exec { pnTable.get("privacyNoticeId" === s"$id") }
      .flatMap {
        case Some(value) => value.leftMap(err => DynamoReadingError(describe(err))).toFuture.some.sequence
        case None        => Future.successful(None)
      }
  }

  override def getByUserId(id: UUID, userId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Option[UserPrivacyNotice]] = {
    logger.info(s"Getting privacy notice with id ${id.toString} for user ${userId.toString}")

    val query: ScanamoOps[List[Either[DynamoReadError, UserPrivacyNotice]]] =
      userTable.query("pnIdWithUserId" === s"$id#$userId")

    scanamo.exec(query).map(x => x.sequence).flatMap {
      case Left(err)   => Future.failed(DynamoReadingError(describe(err)))
      case Right(list) => Future.successful(list.maxByOption(_.version.version))
    }
  }

  override def put(userPrivacyNotice: UserPrivacyNotice)(implicit contexts: Seq[(String, String)]): Future[Unit] = {
    logger.info(s"Putting $userPrivacyNotice privacy notice")
    scanamo.exec(userTable.put(userPrivacyNotice))
  }
}
