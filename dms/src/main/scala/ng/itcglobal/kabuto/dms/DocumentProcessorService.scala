package ng.itcglobal.kabuto
package dms

import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import ng.itcglobal.kabuto._
import core.db.postgres.services.{DocumentMetadata, DocumentMetadataDbService, DocumentMetadataDto}
import core.db.postgres.services.DocumentMetadataDbService.{FileMetaRetrieved, RetrieveDocument, SaveDocument}
import dms.FileManagerService.{AppendFileToDir, FileSearchResponse}

import java.util.{Base64, UUID}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object DocumentProcessorService {
  case class Document(
    fileString: String,
    filePath: String,
    fileNumber: String,
    title: String,
    updatedBy: String
  )

  sealed trait ProcessDocumentCommand
  case class AddDocument(document: Document, replyTo: ActorRef[ProcessDocumentResponse]) extends ProcessDocumentCommand
  case class GetDocument(filePath: String, replyTo: ActorRef[ProcessDocumentResponse]) extends ProcessDocumentCommand

  sealed trait ProcessDocumentResponse
  case class DocumentProcessed(documentId: UUID) extends ProcessDocumentResponse
  case class DocumentProcessingFailed(documentId: String) extends ProcessDocumentResponse
  case class GetDocumentResponse(document: Document) extends ProcessDocumentResponse

  implicit val timeout: Timeout = 3.seconds

  def apply(
             documentMetadataDbServiceActor: ActorRef[DocumentMetadataDbService.MetaCommand],
             fileManagerServiceActor: ActorRef[FileManagerService.FileCommand]
  ): Behavior[ProcessDocumentCommand] =
    Behaviors.receive { (context, message) =>
      implicit val ec: ExecutionContext = context.executionContext
      implicit val scheduler: Scheduler = context.system.scheduler

      message match {
        case req: AddDocument =>
          val fileId = UUID.randomUUID()

          val metadata: DocumentMetadata =
            DocumentMetadataDto(
              fileNumber = req.document.fileNumber,
              title = req.document.title,
              updatedBy = req.document.updatedBy
            ).toDocumentMetadata(fileId)

          val appendFileCommand = AppendFileToDir(
            fileId.toString,
            req.document.fileString,
            metadata.filePath,
            _
          )
          val saveMetadataCommand = SaveDocument(metadata, _)

          val futFileResult = fileManagerServiceActor.ask(appendFileCommand)
          val futMetaResult = documentMetadataDbServiceActor.ask(saveMetadataCommand)

          // should use for comprehension

          futFileResult.onComplete {
            case Success(_) =>
              futMetaResult.onComplete {
                case Success(fileMetaResponse) =>
                  fileMetaResponse match {
                    case DocumentMetadataDbService.FileMetaSavedSuccessfully(documentId) =>
                      req.replyTo ! DocumentProcessed(documentId)
                    case failure: DocumentMetadataDbService.FileMetaFailureResponse =>
                      req.replyTo ! DocumentProcessingFailed(failure.reason)
                  }
                case Failure(exception) =>
                  req.replyTo ! DocumentProcessingFailed(s"File meta saved successfully, but physical file failed with: ${exception.toString}")
              }

            case Failure(exception) =>
              req.replyTo ! DocumentProcessingFailed(s"Unable to process physical (and also aborting meta data processing as well), file failed with: ${exception.toString}")
          }

          Behaviors.same

        case GetDocument(filePath, replyTo) =>
          val futFile: Future[FileManagerService.FileResponse] =
            fileManagerServiceActor.ask(FileManagerService.GetFileByPath(filePath, _))

          val fileId = UUID.fromString(filePath.split("/").last)

          val futMeta: Future[DocumentMetadataDbService.FileMetaResponse] =
            documentMetadataDbServiceActor.ask(RetrieveDocument(fileId,_))

          futFile onComplete {
            case Success(response) =>
              response match {
                case FileSearchResponse(fileString) =>
                  futMeta onComplete {
                    case Success(mRes) =>
                      mRes match {
                        case FileMetaRetrieved(meta) =>
                          val doc = Document(
                              fileString.head,
                              meta.filePath,
                              meta.fileNumber,
                              meta.title,
                              meta.updatedBy.getOrElse("")
                          )

                          replyTo ! GetDocumentResponse(doc)
                        case _ =>
                          replyTo ! DocumentProcessingFailed(filePath)
                      }
                    case Failure(err) =>
                      replyTo ! DocumentProcessingFailed(err.toString)
                  }
                case _ =>
                  replyTo ! DocumentProcessingFailed(filePath)
              }
            case Failure(err) =>
              replyTo ! DocumentProcessingFailed(err.toString)
          }

          for {
            fileResponse <- futFile
            metaResponse <- futMeta
          } yield (fileResponse, metaResponse) match {
            case (FileSearchResponse(file), FileMetaRetrieved(meta)) =>
              replyTo ! GetDocumentResponse(
                  Document(
                    file.head,
                    meta.filePath,
                    meta.fileNumber,
                    meta.title,
                    meta.updatedBy.getOrElse("")
                  )
                )
            case _ =>
              replyTo ! DocumentProcessingFailed(filePath)
          }

          Behaviors.same
      }

    }
}
