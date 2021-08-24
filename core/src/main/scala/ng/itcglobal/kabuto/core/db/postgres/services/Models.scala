package ng.itcglobal.kabuto
package core.db.postgres.services

import java.time.Instant
import java.util.UUID

object Models {
  case class Document (
    documentId:     UUID,
    fileNumber:     String,
    applicantName:  String,
    fileContent:    String,
    uploadedBy:     String,
    uploadedOn:     Instant
  )

  // TODO:
  // turn fileNumber into value class RES-2002-0743
  // with pattern to improve type safety.
}
