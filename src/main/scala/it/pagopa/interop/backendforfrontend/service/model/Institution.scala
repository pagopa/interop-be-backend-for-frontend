package it.pagopa.interop.backendforfrontend.service.model

import java.util.UUID

final case class Institution(
  id: UUID,
  originId: String,
  origin: String,
  description: String,
  digitalAddress: String,
  subUnitType: Option[String]
)
