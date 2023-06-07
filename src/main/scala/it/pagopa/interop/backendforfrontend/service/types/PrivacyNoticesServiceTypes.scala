package it.pagopa.interop.backendforfrontend.service.types

import it.pagopa.interop.backendforfrontend.service.{model => PersistentModel}
import it.pagopa.interop.backendforfrontend.model._
import java.util.UUID

object PrivacyNoticesServiceTypes {

  implicit class UserPrivacyNoticeConverter(private val pn: PersistentModel.UserPrivacyNotice) extends AnyVal {
    def toApi(firstAccept: Boolean, isUpdated: Boolean, latestVersionId: UUID): PrivacyNotice = PrivacyNotice(
      id = pn.privacyNoticeId,
      userId = pn.userId,
      consentType = pn.version.kind.toApi,
      firstAccept = firstAccept,
      isUpdated = isUpdated,
      latestVersionId = latestVersionId
    )
  }

  implicit class PersistentPrivacyNoticeKindConverter(private val k: PersistentModel.PrivacyNoticeKind) extends AnyVal {
    def toApi: ConsentType = k match {
      case PersistentModel.PrivacyNoticeKind.PP  => ConsentType.PP
      case PersistentModel.PrivacyNoticeKind.TOS => ConsentType.TOS
    }
  }

  implicit class PrivacyNoticeKindConverter(private val ct: ConsentType) extends AnyVal {
    def toPersistent: PersistentModel.PrivacyNoticeKind = ct match {
      case ConsentType.PP  => PersistentModel.PrivacyNoticeKind.PP
      case ConsentType.TOS => PersistentModel.PrivacyNoticeKind.TOS
    }
  }

}
