package ng.itcglobal.kabuto.dms
import java.time.LocalDateTime
import java.util.UUID

case class DocumentMetadata(
  id: UUID,
  filePath: String,
  fileNumber: String,
  fileType: String,
  title: String,
  dateCaptured: LocalDateTime,
  dateLastUpdated: Option[LocalDateTime],
  numberOfPages: Int,
  createdBy: String,
  updatedBy: Option[String]
)
