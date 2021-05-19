package ng.itcglobal.kabuto
package core.db.postgres.services

import ng.itcglobal.kabuto._
import core.util.Config

import java.time.LocalDateTime
import java.util.UUID

case class DocumentMetadata(
  id: UUID,
  filePath: String,
  fileNumber: String,
  fileType: String,
  title: String,
  capturedAt: LocalDateTime = LocalDateTime.now(),
  updatedAt: Option[LocalDateTime],
  createdBy: String,
  updatedBy: Option[String]
)

case class DocumentMetadataDto(
	fileNumber: String,
	title: String,
	updatedBy: String
) {
	def toDocumentMetadata(id: UUID): DocumentMetadata = DocumentMetadata(
		id,
		filePath = s"${Config.filesDirectory}$fileNumber/${id.toString}",
		fileNumber,
		fileType = "",
		title,
		capturedAt = LocalDateTime.now(),
		updatedAt = Some(LocalDateTime.now()),
		createdBy = "",
		updatedBy = None
	)
}
