package ng.itcglobal.kabuto
package core.db.postgres.services

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import cats.implicits.catsSyntaxTuple2Semigroupal
import ng.itcglobal.kabuto._
import core.db.postgres.services.DocumentMetadataDbService._
import core.db.postgres.Tables.DocumentMetadata
import core.db.postgres.DatabaseContext
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.BeforeAndAfterEach

import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class DocumentMetadataDbServiceSpec extends ScalaTestWithActorTestKit
 with AnyWordSpecLike with BeforeAndAfterEach with DatabaseContext {
  
  import doobie.implicits._
  import quillContext._

  val probe = testKit.createTestProbe[DocumentMetadataDbService.DocumentMetadataResponse]()
  val documentDbService = testKit.spawn(DocumentMetadataDbService(), "document-db-test-service")

  val docMetaId1: UUID = UUID.randomUUID()
  val docMetaId2: UUID = UUID.randomUUID()

  val documentMetadata: DocumentMetadata = DocumentMetadata(
    docMetaId1,
    s"/documents/tiffs/$docMetaId1",
    "RES/2020/23",
    "tiff",
    "Land Acquisition",
    LocalDateTime.now,
    None,
    "Kabuto",
    None
  )
  val docuMeta2: DocumentMetadata = DocumentMetadata(
    docMetaId2,
    s"/documents/tiffs/$docMetaId2",
    "COM/2020/99",
    "tiff",
    "Office spaces new zoo road square",
    LocalDateTime.now,
    None,
    "Shinobi",
    None
  )

  val docsList = Seq(documentMetadata, docuMeta2)
//TODO
/*
  override def beforeEach(): Unit = {
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
*/
  "DocumentMetaDbService" must {

    "insert new document meta into the database" in {
      documentDbService ! SaveDocumentMetadata(documentMetadata, probe.ref)
      probe.expectMessage(DocumentMetadataSaved(documentMetadata.id))
    }

    "retrieve document meta from the database" in {
      documentDbService ! SaveDocumentMetadata(documentMetadata, probe.ref)
      documentDbService ! RetrieveDocumentMetadata(documentMetadata.fileNumber, documentMetadata.fileType, probe.ref)
      probe.expectMessage(DocumentMetadataSaved(documentMetadata.id))
    }

    "get all counts with one 1 record when only 1 exist in the database" in {
      documentDbService ! GetDocumentMetadataCount(probe.ref)
      probe.awaitAssert(AllDocumentsMetadataCount(1))
    }

    "return zero (0) value for counts from the database when there are no records" in {
      documentDbService ! GetDocumentMetadataCount(probe.ref)
      probe.awaitAssert(AllDocumentsMetadataCount(0))
    }

    s"return the collection of all (${docsList.length} of them) documents metadata from the database" in {
      
      // Populate the record with the test docs meta (2)
      documentDbService ! SaveDocumentMetadata(documentMetadata, probe.ref)
      probe.expectMessage(DocumentMetadataSaved(documentMetadata.id))

//      documentDbService ! SaveDocument(docuMeta2, probe.ref)
//      probe.expectMessage(FileMetaSavedSuccessfully(docuMeta2.id))

//      documentDbService ! GetAllDocumentsMetadata(probe.ref)
//      probe.expectMessage(AllDocumentsMetadata(docsList))
    }

  }

}
