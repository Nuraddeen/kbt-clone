package ng.itcglobal.kabuto
package dms

import java.util.UUID

import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.Behaviors
import cats.implicits.catsSyntaxTuple2Semigroupal
import better.files._

import ng.itcglobal.kabuto._
import core.db.postgres.DatabaseContext
import core.db.postgres.services.DocumentMetadataDbService
import dms.FileManagerService.{AppendFileToDir, FileProcessOK}


class DocumentProcessorServiceSpec extends ScalaTestWithActorTestKit
 with AnyWordSpecLike
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with DatabaseContext {
  import doobie.implicits._
  import DocumentProcessorService._
  import DocumentMetadataDbService._

  val probeDbServiceBeharior = testKit.createTestProbe[DocumentMetadataDbService.DocumentMetadataCommand]
  val mockedDbServiceBehavior = Behaviors.receiveMessage[DocumentMetadataDbService.DocumentMetadataCommand] {
    case SaveDocumentMetadata(document, replyTo) =>
      replyTo ! DocumentMetadataSaved(docId)
      Behaviors.same
  }
  val probeDbServiceActor = testKit.spawn(Behaviors.monitor(probeDbServiceBeharior.ref, mockedDbServiceBehavior))

  val probeFileServiceBehavior = testKit.createTestProbe[FileManagerService.FileCommand]
  val mockedFileServiceBehavior = Behaviors.receiveMessage[FileManagerService.FileCommand]{
    case AppendFileToDir(_, _, _, replyTo) =>
      replyTo ! FileProcessOK
      Behaviors.same
  }
  val probeFileServiceActor = testKit.spawn(Behaviors.monitor(probeFileServiceBehavior.ref, mockedFileServiceBehavior))

  val probe = testKit.createTestProbe[DocumentProcessorService.ProcessDocumentResponse]("probe-document-processor-service")
  val docProcService = testKit.spawn(DocumentProcessorService(probeDbServiceActor.ref, probeFileServiceActor.ref))

  implicit val ec = testKit.system.executionContext

  val docId = UUID.randomUUID()
  val fileDestination = "data/dms/test/dir/"
  val singleTiffFile = "data/dms/com-23-56.tif"

  val file: Array[Byte] = File(singleTiffFile).loadBytes

  val fileBase64String: String = java.util.Base64.getEncoder.encodeToString(file)
  val filePath: String = fileDestination
  val fileNumber: String = "COM-23-56"
  val title: String = "Commercial Application"
  val updatedBy: String = "Nura YII"

  val document: DocumentDto = DocumentDto(fileBase64String, filePath, fileNumber, title, updatedBy)


  override def afterAll(): Unit = testKit.shutdownTestKit()

  override def beforeEach(): Unit = {
    val drop =
      sql"""DROP TABLE IF EXISTS document_metadata""".update.run

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
         """.update
        .run

    (drop, create)
      .mapN(_ + _)
      .transact(xa)
      .unsafeRunSync()
  }

  "Document Processor Service" should {
    "process new file processing request" in {

      docProcService ! AddDocument(document, probe.ref)
      probe.expectMessage(DocumentProcessed(docId))
    }

    "failed to process file request when file is not valid" in {
//      docProcService ! req
//      probe.expectNoMessage
    }
  }

}
