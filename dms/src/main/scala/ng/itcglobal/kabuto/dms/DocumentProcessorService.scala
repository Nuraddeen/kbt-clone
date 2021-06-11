package ng.itcglobal.kabuto
package dms

import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import ng.itcglobal.kabuto._
import core.db.postgres.services.{DocumentMetadata, DocumentMetadataDbService, DocumentMetadataDto}
import core.db.postgres.services.DocumentMetadataDbService._
import dms.FileManagerService._

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
            req.document.filePath,
            _
          )

          val futFileResult = fileManagerServiceActor.ask(appendFileCommand)
          val futMetaResult = documentMetadataDbServiceActor.ask(SaveDocument(metadata, _))

          for {
            fileResponse <- futFileResult
            metaResponse <- futMetaResult
          } yield (fileResponse, metaResponse) match {
            case (FileProcessOK, FileMetaSavedSuccessfully(documentId)) =>
              req.replyTo ! DocumentProcessed(documentId)
            case _ =>
              req.replyTo ! DocumentProcessingFailed(s"unable to process file ${req.document.fileNumber}")
          }

          Behaviors.same

        case GetDocument(filePath, replyTo) =>
          val fileId = UUID.fromString(filePath.split("/").last)
          
          val futFile: Future[FileManagerService.FileResponse] =
            fileManagerServiceActor.ask(FileManagerService.GetFileByPath(filePath, _))

          val futMeta: Future[DocumentMetadataDbService.FileMetaResponse] =
            documentMetadataDbServiceActor.ask(RetrieveDocument(fileId,_))

          // get the meta from db
          // use the meta file path to fetch the physical

          // futFile onComplete {
          //   case Success(response) =>
          //     response match {
          //       case FileSearchResponse(fileString) =>
          //         futMeta onComplete {
          //           case Success(mRes) =>
          //             mRes match {
          //               case FileMetaRetrieved(meta) =>
          //                 val doc = Document(
          //                     fileString.head,
          //                     meta.filePath,
          //                     meta.fileNumber,
          //                     meta.title,
          //                     meta.updatedBy.getOrElse("")
          //                 )

          //                 replyTo ! GetDocumentResponse(doc)
          //               case _ =>
          //                 replyTo ! DocumentProcessingFailed(filePath)
          //             }
          //           case Failure(err) =>
          //             replyTo ! DocumentProcessingFailed(err.toString)
          //         }
          //       case _ =>
          //         replyTo ! DocumentProcessingFailed(filePath)
          //     }
          //   case Failure(err) =>
          //     replyTo ! DocumentProcessingFailed(err.toString)
          // }

          documentMetadataDbServiceActor.ask(RetrieveDocument(fileId,_)) onComplete {
            case Success(metaResponse) =>
              metaResponse match {
                case FileMetaRetrieved(fileMeta) => 
                  fileManagerServiceActor.ask(FileManagerService.GetSinglePageFromFile(fileMeta.filePath + ".tiff", _)) onComplete {
                    case Success(value) =>
                      value match {
                        case FileSearchResponse(files) =>
                          val document = Document(
                            files.head,
                            fileMeta.filePath,
                            fileMeta.fileNumber,
                            fileMeta.title,
                            fileMeta.updatedBy.getOrElse("")
                          )
                          replyTo ! GetDocumentResponse(document)
                        case FileResponseError(msg) =>
                          replyTo ! DocumentProcessingFailed(msg)
                        case _  =>
                          replyTo ! DocumentProcessingFailed(s"Unkown error happend, please try again later.")
                      }
                    case Failure(exception) =>
                      replyTo ! DocumentProcessingFailed(exception.toString)
                  }
                case FileMetaFailureResponse(failure) =>
                  replyTo ! DocumentProcessingFailed(failure)
                case _ =>
                  replyTo ! DocumentProcessingFailed(s"Unknown error happened, please try again later.")
              }

            case Failure(exception) =>
              replyTo ! DocumentProcessingFailed(exception.toString)
          }

          // for {
          //   fileResponse <- futFile
          //   metaResponse <- futMeta
          // } yield (fileResponse, metaResponse) match {
          //   case (FileSearchResponse(file), FileMetaRetrieved(meta)) =>
          //     replyTo ! GetDocumentResponse(
          //         Document(
          //           file.head,
          //           meta.filePath,
          //           meta.fileNumber,
          //           meta.title,
          //           meta.updatedBy.getOrElse("")
          //         )
          //       )
          //   case _ =>
          //     replyTo ! DocumentProcessingFailed("unable to retrieve file at the moment")
          // }

          Behaviors.same
      }

    }
}
