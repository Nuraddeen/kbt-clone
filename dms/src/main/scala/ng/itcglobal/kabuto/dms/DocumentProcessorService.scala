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

import spray.json._

import ng.itcglobal.kabuto._
import core.db.postgres.services.{DocumentMetadata, DocumentMetadataDbService}
import core.db.postgres.services.DocumentMetadataDbService._
import core.util.{Config}
import core.util.Enum.HttpResponseStatus
import core.util.Util.BetasoftApiHttpResponse
import dms.FileManagerService._


object DocumentProcessorService extends JsonSupport {
  
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
  case class DataResponse(response: BetasoftApiHttpResponse) extends ProcessDocumentResponse
  case class ErrorResponse(message: String)               extends ProcessDocumentResponse
  //case class DocumentProcessed(documentId: UUID) extends ProcessDocumentResponse
  //case class DocumentProcessingFailed(documentId: String) extends ProcessDocumentResponse
  //case class DocumentFound(documentDto: DocumentDto) extends ProcessDocumentResponse

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
                                    req.replyTo ! DataResponse(
                                      BetasoftApiHttpResponse(
                                        status      = HttpResponseStatus.Success,
                                        description = "Document Saved",
                                        code        = Some(HttpResponseStatus.Success.id)
                                      )
                                    )
                                 case _ =>
                                    //Todo: Do we need to delete the file which was already saved?
                                    req.replyTo ! DataResponse(
                                      BetasoftApiHttpResponse(
                                        status      = HttpResponseStatus.Failed,
                                        description = "Could not save file meta data",
                                        code        = Some(HttpResponseStatus.Failed.id)
                                      )
                                    )
                                    
                            }
                          case Failure (error) => 
                            req.replyTo ! ErrorResponse(s"Could not save file meta data")
                        }
                      case _ => 
                         req.replyTo ! DataResponse(
                                      BetasoftApiHttpResponse(
                                        status      = HttpResponseStatus.Failed,
                                        description = "Could not save file to disk",
                                        code        = Some(HttpResponseStatus.Failed.id)
                                      )
                                    )
                }
            case Failure (error) => 
              req.replyTo ! ErrorResponse(s"Could not save file to disk")

          }
     
          Behaviors.same

        case req: GetDocument =>
       
          documentMetadataDbServiceActor.ask(RetrieveDocumentMetadata(req.fileNumber, req.fileType, _)) onComplete {
            case Success(metaResponse) =>
              metaResponse match {
                case DocumentMetadataRetrieved(docMetadataList) => 
                    docMetadataList match {
                        case Nil => //no metadata was found
                        req.replyTo ! DataResponse(
                                      BetasoftApiHttpResponse(
                                        status      = HttpResponseStatus.NotFound,
                                        description = "No document metadata found",
                                        code        = Some(HttpResponseStatus.NotFound.id)
                                      )
                                    )
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
                                req.replyTo !  DataResponse(
                                      BetasoftApiHttpResponse(
                                        status      = HttpResponseStatus.Success,
                                        description = "Document retrieved",
                                        code        = Some(HttpResponseStatus.Success.id),
                                        data        = Some(documentDto.toJson)
                                      )
                                    )

                              case FileResponseError(msg) =>
                                req.replyTo ! DataResponse(
                                      BetasoftApiHttpResponse(
                                        status      = HttpResponseStatus.Failed,
                                        description = msg,
                                        code        = Some(HttpResponseStatus.Failed.id) 
                                      )
                                    ) 
                              case _  =>
                                req.replyTo !  DataResponse(
                                      BetasoftApiHttpResponse(
                                        status      = HttpResponseStatus.Failed,
                                        description = "Unkown error occurred, please try again later.",
                                        code        = Some(HttpResponseStatus.Failed.id) 
                                      )
                                    )   
                            }
                          case Failure(exception) =>
                            log.error(s"Could not retrieve file string $exception, for the request $req")
                            req.replyTo ! ErrorResponse("Could not retrieve file string")
                        }
                 }
                case _ =>
                 req.replyTo !  DataResponse(
                                  BetasoftApiHttpResponse(
                                    status      = HttpResponseStatus.Failed,
                                    description = "Could not retrieve document metadata, please try again later.",
                                    code        = Some(HttpResponseStatus.Failed.id) 
                                  )
                                )  
              }

            case Failure(exception) =>
              req.replyTo ! ErrorResponse("Error retrieving document metadata, please try again later.")
          }

          Behaviors.same
      }

    }


}
