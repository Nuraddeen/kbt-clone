package ng.itcglobal.kabuto.core.db.postgres.services

import java.time.LocalDateTime
import java.util.UUID

case class DocumentMetadata(
  id: UUID,
  filePath: String,
  fileNumber: String,
  fileType: String,
  title: String,
  capturedAt: LocalDateTime,
  updatedAt: Option[LocalDateTime],
  createdBy: String,
  updatedBy: Option[String]
)
