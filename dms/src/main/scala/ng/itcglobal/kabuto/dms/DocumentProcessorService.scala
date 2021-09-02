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
import core.util.{Config, Enum}
import dms.FileManagerService._

object DocumentProcessorService {
  
  case class DocumentDto(
    fileString: String,
    fileNumber: String,
    fileType: String,
    title: String,
    fileExtension: String,
    createdBy: String,
    updatedBy: Option[String] = None
  ) {

  def generateFilePath(): String = {
      val fn: String = fileNumber.toLowerCase
                .flatMap {
                  case ' ' | '/' | '.' => ""
                  case s => s"$s"
                }
      s"${Config.filesDirectory}/$fn" 
  }


 
    def removeFileTypePrefix() : String ={
      fileString.split(s"data:image/$fileExtension;base64,").last
    }

   

}

  sealed trait ProcessDocumentCommand
  case class AddDocument(documentDto: DocumentDto, replyTo: ActorRef[ProcessDocumentResponse]) extends ProcessDocumentCommand
  case class GetDocument(fileNumber: String, fileType: String, replyTo: ActorRef[ProcessDocumentResponse]) extends ProcessDocumentCommand

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
      val log = context.log

      message match {
        case req: AddDocument =>
          val fileId = UUID.randomUUID()
          val filePath = req.documentDto.generateFilePath()

          val saveFileCommand = SaveFileToDir(
            filename   = fileId.toString,
            fileString = req.documentDto.removeFileTypePrefix(),
            filePath = filePath,
            extension = Some(req.documentDto.fileExtension),
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

        case req: GetDocument =>
       
          documentMetadataDbServiceActor.ask(RetrieveDocumentMetadata(req.fileNumber, req.fileType, _)) onComplete {
            case Success(metaResponse) =>
              metaResponse match {
                case DocumentMetadataRetrieved(docMetadataList) => 
                    docMetadataList match {
                        case Nil => //no metadata was found
                        req.replyTo ! DocumentProcessingFailed("No document metadata found")
                        case _ =>  //meta data found
                          val docMetaData = docMetadataList.head

                        fileManagerServiceActor.ask(FileManagerService.RetrieveFileString(docMetaData.filePath, _)) onComplete {
                          case Success(fileResponse) =>
                            fileResponse match {
                              case FileRetrievedResponse(fileString) =>
                                val fileExtension = docMetaData.filePath.split('.').last

                                val documentDto = DocumentDto(
                                  fileString     = FileManagerService.getFileTypePrefix(fileExtension) + fileString,
                                  fileNumber     = docMetaData.fileNumber,
                                  fileType       = docMetaData.fileType,
                                  title          = docMetaData.title,
                                  fileExtension  = fileExtension,
                                  createdBy      = docMetaData.createdBy,
                                  updatedBy      = docMetaData.updatedBy,
                                )
                                req.replyTo ! DocumentFound(documentDto)
                              case FileResponseError(msg) =>
                                req.replyTo ! DocumentProcessingFailed(msg)
                              case _  =>
                                req.replyTo ! DocumentProcessingFailed(s"Unkown error occured, please try again later.")
                            }
                          case Failure(exception) =>
                            log.error(s"Could not retrieve file string $exception, for the request $req")
                            req.replyTo ! DocumentProcessingFailed("Could not retrieve file string")
                        }
                 }
                case _ =>
                 req.replyTo ! DocumentProcessingFailed(s"Could not retrieve document metadata, please try again later.")
              }

            case Failure(exception) =>
              req.replyTo ! DocumentProcessingFailed("Error retrieving document metadata, please try again later.")
          }

          Behaviors.same
      }

    }


}
