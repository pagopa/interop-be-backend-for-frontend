package it.pagopa.interop.backendforfrontend.service.model

import org.scanamo.DynamoFormat
import org.scanamo.generic.semiauto.deriveDynamoFormat

import java.util.UUID
import java.time.OffsetDateTime

final case class UserPrivacyNotice(
  pk: String,
  sk: String,
  pnId: UUID,
  userId: UUID,
  acceptedAt: OffsetDateTime,
  version: UserPrivacyNoticeVersion
)

final case class UserPrivacyNoticeVersion(versionId: UUID, kind: PrivacyNoticeKind, version: Int)

sealed trait PrivacyNoticeKind

object PrivacyNoticeKind {
  case object TOS extends PrivacyNoticeKind
  case object PP  extends PrivacyNoticeKind
}

object UserPrivacyNotice {

  /**
    * PRNO stands for [PR]IVACY [NO]TICE UUID, thus you get soon the meaning of the column
    */
  val pkPrefix: String = "PRNO#"

  /**
    * PRNO stands for [USER] UUID, thus you get soon the meaning of the column, 
    * chained to this you can find the number of the version separate by # , simulating the One To Many cardinality
    * using a single table pattern
    */
  val skPrefix: String = "USER#"

  implicit val formatPrivacyNoticeKind: DynamoFormat[PrivacyNoticeKind]               = deriveDynamoFormat
  implicit val formatUserPrivacyNoticeVersion: DynamoFormat[UserPrivacyNoticeVersion] = deriveDynamoFormat
  implicit val formatUserPrivacyNotice: DynamoFormat[UserPrivacyNotice]               = deriveDynamoFormat
}
