package ng.itcglobal.kabuto
package core.db.postgres.services

import java.time.LocalDateTime
import java.util.UUID
import org.scalatest.wordspec.AnyWordSpecLike
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import DocumentMetadataDbService._
import cats.implicits.catsSyntaxTuple2Semigroupal
import ng.itcglobal.kabuto.core.db.postgres.DatabaseContext

class DocumentMetadataDbServiceSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with DatabaseContext {
  import doobie.implicits._
  import quillContext._

  val probe = testKit.createTestProbe[DocumentMetadataDbService.Response]()
  val documentDbService = testKit.spawn(DocumentMetadataDbService(), "document-db-service")
  val documentMetadata = DocumentMetadata(
    UUID.randomUUID(),
    "/documents/tiffs/",
    "RES/2020/23",
    "tiff",
    "Land Acquisition",
    LocalDateTime.now,
    None,
    "Kabuto",
    None
  )

  def runOnce(): Index = {
    val drop =
      sql"""DROP TABLE IF EXISTS document_metadata"""
        .update
        .run

    val create =
      sql"""
        CREATE TABLE IF NOT EXISTS document_metadata
        (
            id          uuid      NOT NULL PRIMARY KEY,
            file_path   TEXT      NOT NULL UNIQUE,
            file_number VARCHAR   NOT NULL UNIQUE,
            file_type   VARCHAR   NOT NULL,
            title       VARCHAR   NOT NULL,
            captured_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            created_by  VARCHAR   NOT NULL,
            updated_by  VARCHAR
        )
      """
      .update
      .run

    (drop, create)
      .mapN(_ + _)
      .transact(xa)
      .unsafeRunSync()
  }

  def beforeEach(): Index = {
    sql"""DELETE FROM document_metadata""".update.run
      .transact(xa)
      .unsafeRunSync()
  }

  "DocumentMetaDbService" must {
    runOnce()

    "insert new document meta into the database" in {
      beforeEach()
      documentDbService ! SaveDocument(documentMetadata, probe.ref)
      probe.expectMessage(SuccessResponse(documentMetadata.id.toString))
    }

    "retrieve document meta from the database" in {
      documentDbService ! RetrieveDocument(documentMetadata.id, probe.ref)
      probe.expectMessage(SuccessResponse(documentMetadata.filePath))
    }
  }

}
