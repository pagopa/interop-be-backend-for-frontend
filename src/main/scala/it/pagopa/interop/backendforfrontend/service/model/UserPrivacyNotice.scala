package it.pagopa.interop.backendforfrontend.service.model

import org.scanamo.DynamoFormat
import org.scanamo.generic.semiauto.deriveDynamoFormat

import java.util.UUID
import java.time.OffsetDateTime

final case class UserPrivacyNotice(
  pnIdWithUserId: String,
  versionNumber: Int,
  privacyNoticeId: UUID,
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
  implicit val formatPrivacyNoticeKind: DynamoFormat[PrivacyNoticeKind]               = deriveDynamoFormat
  implicit val formatUserPrivacyNoticeVersion: DynamoFormat[UserPrivacyNoticeVersion] = deriveDynamoFormat
  implicit val formatUserPrivacyNotice: DynamoFormat[UserPrivacyNotice]               = deriveDynamoFormat
}
