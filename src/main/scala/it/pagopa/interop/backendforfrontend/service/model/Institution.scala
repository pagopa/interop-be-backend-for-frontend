package it.pagopa.interop.backendforfrontend.service.model

import java.util.UUID

final case class Institution(
  id: UUID,
  originId: String,
  origin: String,
  taxCode: String,
  subunitCode: Option[String],
  description: String,
  digitalAddress: String,
  subUnitType: Option[String]
)
