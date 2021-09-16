package ng.itcglobal.kabuto
package dms

import java.util.UUID
import scala.util.{Failure, Success}

import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import cats.implicits.catsSyntaxTuple2Semigroupal
import better.files._

import ng.itcglobal.kabuto._
import core.db.postgres.DatabaseContext
import core.db.postgres.services._
import core.db.postgres.Tables._
import core.util.Enum.HttpResponseStatus
import core.util.Util.BetasoftApiHttpResponse 
import DocumentProcessorService._
import DocumentMetadataDbService._


class DocumentProcessorServiceSpec extends ScalaTestWithActorTestKit
  with AnyWordSpecLike
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with DatabaseContext {

  import doobie.implicits._
  import quillContext._


  val fileManagerService = testKit.spawn(FileManagerService(), "file-manager-service")
  val docMetaDataDbService = testKit.spawn(DocumentMetadataDbService(), "doc-metadata-db-service")
  val docProcessorService = testKit.spawn(DocumentProcessorService(docMetaDataDbService, fileManagerService), "doc-processor-service")

  val docProcessorProbe  = testKit.createTestProbe[DocumentProcessorService.ProcessDocumentResponse]()
  val docMetadataDbProbe = testKit.createTestProbe[DocumentMetadataDbService.DocumentMetadataResponse]()
  val fileManagerProbe   = testKit.createTestProbe[FileManagerService.FileResponse]()

  implicit val ec = testKit.system.executionContext
  implicit val scheduler: Scheduler = testKit.system.scheduler
 

  val fileString = "data:image/webp;base64,UklGRkIDAABXRUJQVlA4WAoAAAAgAAAAEgAAEQAASUNDUKACAAAAAAKgbGNtcwQwAABtbnRyUkdCIFhZWiAH5QAJAAEACgAaABJhY3NwQVBQTAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA9tYAAQAAAADTLWxjbXMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA1kZXNjAAABIAAAAEBjcHJ0AAABYAAAADZ3dHB0AAABmAAAABRjaGFkAAABrAAAACxyWFlaAAAB2AAAABRiWFlaAAAB7AAAABRnWFlaAAACAAAAABRyVFJDAAACFAAAACBnVFJDAAACFAAAACBiVFJDAAACFAAAACBjaHJtAAACNAAAACRkbW5kAAACWAAAACRkbWRkAAACfAAAACRtbHVjAAAAAAAAAAEAAAAMZW5VUwAAACQAAAAcAEcASQBNAFAAIABiAHUAaQBsAHQALQBpAG4AIABzAFIARwBCbWx1YwAAAAAAAAABAAAADGVuVVMAAAAaAAAAHABQAHUAYgBsAGkAYwAgAEQAbwBtAGEAaQBuAABYWVogAAAAAAAA9tYAAQAAAADTLXNmMzIAAAAAAAEMQgAABd7///MlAAAHkwAA/ZD///uh///9ogAAA9wAAMBuWFlaIAAAAAAAAG+gAAA49QAAA5BYWVogAAAAAAAAJJ8AAA+EAAC2xFhZWiAAAAAAAABilwAAt4cAABjZcGFyYQAAAAAAAwAAAAJmZgAA8qcAAA1ZAAAT0AAACltjaHJtAAAAAAADAAAAAKPXAABUfAAATM0AAJmaAAAmZwAAD1xtbHVjAAAAAAAAAAEAAAAMZW5VUwAAAAgAAAAcAEcASQBNAFBtbHVjAAAAAAAAAAEAAAAMZW5VUwAAAAgAAAAcAHMAUgBHAEJWUDggfAAAADAEAJ0BKhMAEgA+MRaIQyIhIRQGqCADBLKAVhfgQGkJLS4K03F2aq98AAD+/OmdvtxT9mtzo6TyHq6/4nzyRz0f5OWb0zDYLfmOvIo5h0eCyqzk96EgUTeEUC+RG8CdXFPA3PYfp/Cqqcf9rji60s/ppVusaT8yPEK6AAA="
  val webpFileExtension = "webp"
  val invalidFileExtension = "zzz"
  val fileNumber = "Test01"
  val fileType = "Test File"

  val documentDto = DocumentDto(
    fileString =  fileString,
    fileNumber = fileNumber,
    fileType = fileType,
    title = "Testing Document Service",
    fileExtension = webpFileExtension,
    createdBy = "TestScript"
  )

  val savedResponse = DocumentProcessorService.DataResponse(
                 BetasoftApiHttpResponse(
                    status      = HttpResponseStatus.Success,
                    description = "Document Saved",
                    code        = Some(HttpResponseStatus.Success.id)
                  )
        )


  

  

  override def beforeEach() : Unit = {
    //delete all test file metadata 
      quillContext.run(
          query[DocumentMetadata]
            .filter(meta => meta.fileNumber.equals(lift(fileNumber)) && meta.fileType.equals(lift(fileType)) )
            .delete
            )
        .transact(xa)
        .unsafeRunSync()

    val testFilesDir = File(documentDto.generateFilePath())
    testFilesDir.clear()//delete all files in the path
  }




  "Document Processor Service" should {
    "successfully save new file request" in {

      docProcessorService ! AddDocument(documentDto, docProcessorProbe.ref)
      
      docProcessorProbe.expectMessage(savedResponse)

    }

    "fail to add the file when it's extension is invalid" in {

              docProcessorService ! AddDocument(
                DocumentDto(
                  fileString =  fileString,
                  fileNumber = "Test01",
                  fileType = "Test",
                  title = "Test File",
                  fileExtension = invalidFileExtension,
                  createdBy = "TestScript"
                ), docProcessorProbe.ref)
                
            docProcessorProbe.expectMessage(DocumentProcessorService.ErrorResponse(s"Could not save file to disk"))

    }

    "fail to add the file when the file string is invalid" in {

           docProcessorService ! AddDocument(
             DocumentDto(
                fileString =  "Invalid file string",
                fileNumber = "Test01",
                fileType = "Test",
                title = "Test File",
                fileExtension = invalidFileExtension,
                createdBy = "TestScript"
              ), docProcessorProbe.ref)

        docProcessorProbe.expectMessage(DocumentProcessorService.ErrorResponse("Could not save file to disk"))
       }



      "delete all existing files with the same file number and file type" ignore {

          //add document
          //add again
          //prove that the first document does not exists by getting it

          docProcessorService ! AddDocument(documentDto, docProcessorProbe.ref)
          docProcessorProbe.expectMessage(savedResponse)

          //there is no way to get the uuid that was saved and it is required to make asserion here
         // docMetaDataDbService ! RetrieveDocumentMetadata(documentDto.fileNumber, documentDto.fileType, docMetadataDbProbe.ref)
          

          docProcessorService ! AddDocument(documentDto, docProcessorProbe.ref)
          docProcessorProbe.expectMessage(savedResponse)


        }



      "retrieve a saved document with valid file number and file types" in {
                 docProcessorService ! AddDocument(documentDto, docProcessorProbe.ref)
                 docProcessorProbe.expectMessage(savedResponse)

                docProcessorService ! GetDocument(documentDto.fileNumber, documentDto.fileType, docProcessorProbe.ref)
                val docData = DocumentDto(
                    fileString =  fileString,
                    fileNumber = fileNumber,
                    fileType = fileType,
                    title = "Testing Document Service",
                    fileExtension = webpFileExtension,
                    createdBy = "TestScript",
                    updatedBy = None
                  )


                  docProcessorProbe.expectMessage(DocumentProcessorService.DataResponse(
                                   BetasoftApiHttpResponse(
                                      status = HttpResponseStatus.Success,
                                      description = "Document retrieved",
                                      code = Some(HttpResponseStatus.Success.id),
                                      data = Some(docData.toJson)
                                    )
                                    )
                          )
               }
  }

}
