package ng.itcglobal.kabuto
package dms

import java.util.{Base64, UUID}
import java.time.LocalDateTime

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import ng.itcglobal.kabuto._
import core.db.postgres.services.{DocumentMetadata, DocumentMetadataDbService}
import core.db.postgres.services.DocumentMetadataDbService._
import core.util.Config
import dms.FileManagerService._

object DocumentProcessorService {
  
  case class DocumentDto(
    fileString: String,
    fileNumber: String,
    fileType: String,
    title: String,
    extension: String,
    createdBy: String
  )

  sealed trait ProcessDocumentCommand
  case class AddDocument(documentDto: DocumentDto, replyTo: ActorRef[ProcessDocumentResponse]) extends ProcessDocumentCommand
  case class GetDocument(filePath: String, replyTo: ActorRef[ProcessDocumentResponse]) extends ProcessDocumentCommand

  sealed trait ProcessDocumentResponse
  case class DocumentProcessed(documentId: UUID) extends ProcessDocumentResponse
  case class DocumentProcessingFailed(documentId: String) extends ProcessDocumentResponse
  case class DocumentFound(documentDto: DocumentDto) extends ProcessDocumentResponse

  implicit val timeout: Timeout = 3.seconds

  def apply(
    documentMetadataDbServiceActor: ActorRef[DocumentMetadataDbService.DocumentMetadataCommand],
    fileManagerServiceActor: ActorRef[FileManagerService.FileCommand]
  ): Behavior[ProcessDocumentCommand] =
    Behaviors.receive { (context, message) =>
      implicit val ec: ExecutionContext = context.executionContext
      implicit val scheduler: Scheduler = context.system.scheduler

      message match {
        case req: AddDocument =>
          val fileId = UUID.randomUUID()
          val filePath = generateFilePath(req.documentDto.fileNumber)

          val saveFileCommand = SaveFileToDir(
            filename   = fileId.toString,
            fileString = req.documentDto.fileString,
            filePath = filePath,
            extension = Some(req.documentDto.extension),
             _
          )

          fileManagerServiceActor.ask(saveFileCommand) onComplete {

            case Success(saveFileResponse) => 
                saveFileResponse match {
                     case fileSavedRes: FileSavedResponse =>

                      val docMetaData =   DocumentMetadata(
                          id          = fileId,
                          filePath    = fileSavedRes.fullFilePath,
                          fileNumber  = req.documentDto.fileNumber,
                          fileType    = req.documentDto.fileType,
                          title       = req.documentDto.title,
                          capturedAt 	= LocalDateTime.now(),
                          updatedAt 	= Some(LocalDateTime.now()),
                          createdBy   = req.documentDto.createdBy,
                          updatedBy 	= None
                        )
                        documentMetadataDbServiceActor.ask(SaveDocumentMetadata(docMetaData, _))  onComplete {
                          case Success(docMetaResponse) => 
                            docMetaResponse match {
                                 case res: DocumentMetadataSaved => 
                                    req.replyTo ! DocumentProcessed(res.documentId)
                                 case _ =>
                                    //Todo: Do we need to delete the file which was already saved?
                                    req.replyTo ! DocumentProcessingFailed(s"unable to save file meta data")
                            }
                          case Failure (error) => 
                            req.replyTo ! DocumentProcessingFailed(s"unable to save file meta data")
                        }
                      case _ => 
                        req.replyTo ! DocumentProcessingFailed(s"unable to save file to disk")
                }
            case Failure (error) => 
              req.replyTo ! DocumentProcessingFailed(s"unable to save file to disk")

          }
     
          Behaviors.same

        case GetDocument(filePath, replyTo) =>
          val fileId = UUID.fromString(filePath.split("/").last)
          
          /*
          val futFile: Future[FileManagerService.FileResponse] =
            fileManagerServiceActor.ask(FileManagerService.GetFileByPath(filePath, _))
           

          val futMeta: Future[DocumentMetadataDbService.FileMetaResponse] =
            documentMetadataDbServiceActor.ask(RetrieveDocument(fileId,_))
 */
          documentMetadataDbServiceActor.ask(RetrieveDocumentMetadata(fileId,_)) onComplete {
            case Success(metaResponse) =>
              metaResponse match {
                case DocumentMetadataRetrieved(fileMeta) => 
                  fileManagerServiceActor.ask(FileManagerService.GetSinglePageFromFile(fileMeta.filePath + ".tiff", _)) onComplete {
                    case Success(value) =>
                      value match {
                        case FileSearchResponse(files) =>
                          val documentDto = DocumentDto(
                            fileString = files.head,
                            fileNumber = fileMeta.fileNumber,
                            title = fileMeta.title,
                            createdBy = fileMeta.updatedBy.getOrElse(""),
                            extension = "",//Todo
                            fileType = ""
                          )
                          replyTo ! DocumentFound(documentDto)
                        case FileResponseError(msg) =>
                          replyTo ! DocumentProcessingFailed(msg)
                        case _  =>
                          replyTo ! DocumentProcessingFailed(s"Unkown error happend, please try again later.")
                      }
                    case Failure(exception) =>
                      replyTo ! DocumentProcessingFailed(exception.toString)
                  }
                case DocumentMetadataFailure(failure) =>
                  replyTo ! DocumentProcessingFailed(failure)
                case _ =>
                  replyTo ! DocumentProcessingFailed(s"Unknown error happened, please try again later.")
              }

            case Failure(exception) =>
              replyTo ! DocumentProcessingFailed(exception.toString)
          }

          Behaviors.same
      }

    }



    def generateFilePath(fileNumber: String): String = {
      val fn: String = fileNumber.toLowerCase
                .flatMap {
                  case ' ' | '/' | '.' => ""
                  case s => s"$s"
                }
      s"${Config.filesDirectory}/$fn"
                //TODO: blm was hard coded here. 
                //I put it so that we have a dedicated folder for files of any project. Because we may decide to use kabuto for another project, so it will also have its own folder
    }
}
