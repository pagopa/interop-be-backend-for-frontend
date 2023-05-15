package it.pagopa.interop.backendforfrontend.service.model

import org.scanamo.DynamoFormat
import org.scanamo.generic.semiauto.deriveDynamoFormat

import java.util.UUID
import java.time.OffsetDateTime

final case class PrivacyNotice(
  pk: String,
  sk: String,
  pnId: UUID,
  createdDate: OffsetDateTime,
  lastPublishedDate: OffsetDateTime,
  organizationId: UUID,
  responsibleUserId: Option[UUID],
  privacyNoticeVersion: PrivacyNoticeVersion,
  createdAt: OffsetDateTime
)

final case class PrivacyNoticeVersion(
  versionId: UUID,
  name: String,
  publishedDate: OffsetDateTime,
  status: String,
  version: Int
)

object PrivacyNotice {
  val pkPrefix: String                                                        = "PRNO#"
  val skPrefix: String                                                        = "LATV#"
  implicit val formatPrivacyNoticeVersion: DynamoFormat[PrivacyNoticeVersion] = deriveDynamoFormat
  implicit val formatPrivacyNotice: DynamoFormat[PrivacyNotice]               = deriveDynamoFormat
}
