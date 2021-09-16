package ng.itcglobal.kabuto
package core.db.postgres

import java.time.LocalDateTime
import java.util.UUID

object Tables {

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

}