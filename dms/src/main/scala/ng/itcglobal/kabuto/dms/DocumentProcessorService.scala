package ng.itcglobal.kabuto
package dms

import java.util.{UUID}
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
import core.db.postgres.services.DocumentMetadataDbService
import core.db.postgres.services.DocumentMetadataDbService._
import core.db.postgres.Tables.DocumentMetadata
import core.util.Config
import core.util.Enum.HttpResponseStatus
import core.util.Util._
import dms.FileManagerService._

object DocumentProcessorService extends CustomJsonProtocol {

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
          case s               => s"$s"
        }
      s"${Config.filesDirectory}/$fn"
    }

    def removeFileTypePrefix(): String = {
      fileString.split(s"data:image/$fileExtension;base64,").last
    }

  }


   case class DocumentMetaDataPayload(
      fileNumber: String,
      fileType: String,
      title: String,
      capturedAt: LocalDateTime = LocalDateTime.now(),
      updatedAt: Option[LocalDateTime],
      createdBy: String,
      updatedBy: Option[String]
  )


  sealed trait  Command
  case class AddDocument(
      documentDto: DocumentDto,
      replyTo: ActorRef[KabutoApiHttpResponse]
  ) extends Command
  case class GetDocument(
      fileNumber: String,
      fileType: String,
      replyTo: ActorRef[KabutoApiHttpResponse]
  ) extends Command
  case class DeleteDocument(
      docId: UUID,
      filePath: String,
      replyTo: ActorRef[KabutoApiHttpResponse]
  ) extends Command
 
  case class GetAllDocumentsMetadataByFileNumberCommand( 
      fileNumber: String,
      replyTo: ActorRef[KabutoApiHttpResponse]
  ) extends Command


  implicit val timeout: Timeout = 3.seconds

  def apply(
      documentMetadataDbService: ActorRef[
        DocumentMetadataDbService.DocumentMetadataCommand
      ],
      fileManagerServiceActor: ActorRef[FileManagerService.FileCommand]
  ): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      implicit val ec: ExecutionContext = context.executionContext
      implicit val scheduler: Scheduler = context.system.scheduler
      val log = context.log

      message match {
        case req: AddDocument =>
          documentMetadataDbService.ask(
            RetrieveDocumentMetadata(
              req.documentDto.fileNumber,
              req.documentDto.fileType,
              _
            )
          ) onComplete {
            case Success(DocumentMetadataRetrieved(existingMetadaList)) =>

              val fileId = UUID.randomUUID()
              val filePath = req.documentDto.generateFilePath()

              val saveFileCommand = SaveFileToDir(
                filename = fileId.toString,
                fileString = req.documentDto.removeFileTypePrefix(),
                filePath = filePath,
                extension = Some(req.documentDto.fileExtension),
                _
              )
                 
              fileManagerServiceActor.ask(saveFileCommand) onComplete {
                    case Success(fileSavedRes: FileSavedResponse) =>
                      val docMetaData = DocumentMetadata(
                        id = fileId,
                        filePath = fileSavedRes.fullFilePath,
                        fileNumber = req.documentDto.fileNumber,
                        fileType = req.documentDto.fileType,
                        title = req.documentDto.title,
                        capturedAt = LocalDateTime.now(),
                        updatedAt = Some(LocalDateTime.now()),
                        createdBy = req.documentDto.createdBy,
                        updatedBy = None
                      )
                      documentMetadataDbService.ask(
                        SaveDocumentMetadata(docMetaData, _)
                      ) onComplete {
                        case Success(DocumentMetadataSaved(_)) =>

                          //delete existing metadata
                          for (metadata <- existingMetadaList) {
                            fileManagerServiceActor .ask( DeleteFile(metadata.filePath, _))
                            documentMetadataDbService .ask(DeleteDocumentMetadata(metadata.id, _))
                          }

                          req.replyTo ! 
                            KabutoApiHttpResponse(
                              status = HttpResponseStatus.Success,
                              description = "Document Saved",
                              code = Some(HttpResponseStatus.Success.id)
                            )
                          
                        case _ =>
                          // delete the file which was already saved sinces its metdata couldn't be saved
                            fileManagerServiceActor.ask( DeleteFile(fileSavedRes.fullFilePath, _))                         
                            req.replyTo !  
                            KabutoApiHttpResponse(
                              status = HttpResponseStatus.Failed,
                              description = "Could not save file meta data",
                              code = Some(HttpResponseStatus.Failed.id)
                            )
                      }
                    
                    
                      case (Success(res: FileResponseError)) => 

                      req.replyTo ! 
                        KabutoApiHttpResponse(
                              status = HttpResponseStatus.Failed,
                              description = res.msg,
                              code = Some(HttpResponseStatus.Failed.id)
                            )

                    
                    case _ =>
                      
                      req.replyTo ! 
                        KabutoApiHttpResponse(
                              status = HttpResponseStatus.Failed,
                              description = "Could not save file to disk",
                              code = Some(HttpResponseStatus.Failed.id)
                            )
              }

            case Failure(error) => //error fetching exisitn metadata
              log.error(
                s"Could not retrieve existing metadata $error, for the request $req"
              )
              req.replyTo !  
                  KabutoApiHttpResponse(
                              status = HttpResponseStatus.Failed,
                              description =   "Could not retrieve existing file metadata",
                              code = Some(HttpResponseStatus.Failed.id)
                            )

            case _ => //any other error for exisitng meta data retrieval
              req.replyTo !  
               KabutoApiHttpResponse(
                              status = HttpResponseStatus.Failed,
                              description = "Could not retrieve existing file metadata",
                              code = Some(HttpResponseStatus.Failed.id)
                            )
          }

          Behaviors.same

        case req: GetDocument =>
          documentMetadataDbService.ask(
            RetrieveDocumentMetadata(req.fileNumber, req.fileType, _)
          ) onComplete {
            case Success(
                  DocumentMetadataRetrieved(Nil)
                ) => //no metadata was found
              req.replyTo !  
                KabutoApiHttpResponse(
                  status = HttpResponseStatus.NotFound,
                  description = "No document metadata found",
                  code = Some(HttpResponseStatus.NotFound.id)
                
              )
            case Success( DocumentMetadataRetrieved(docMetadataList) ) => //meta data found
              val docMetaData = docMetadataList.head

              fileManagerServiceActor.ask(
                FileManagerService
                  .RetrieveFileString(docMetaData.filePath, _)
              ) onComplete {
                case Success(FileRetrievedResponse(fileString)) =>
                  val fileExtension =
                    docMetaData.filePath.split('.').last

                  val documentDto = DocumentDto(
                    fileString = FileManagerService.getFileTypePrefix(
                      fileExtension
                    ) + fileString,
                    fileNumber = docMetaData.fileNumber,
                    fileType = docMetaData.fileType,
                    title = docMetaData.title,
                    fileExtension = fileExtension,
                    createdBy = docMetaData.createdBy,
                    updatedBy = docMetaData.updatedBy
                  )
                  req.replyTo !
                    KabutoApiHttpResponse(
                      status = HttpResponseStatus.Success,
                      description = "Document retrieved",
                      code = Some(HttpResponseStatus.Success.id),
                      data = Some(documentDto.toJson)
                    )
                  

                case Success(FileResponseError(msg)) =>
                  req.replyTo !
                    KabutoApiHttpResponse(
                      status = HttpResponseStatus.Failed,
                      description = msg,
                      code = Some(HttpResponseStatus.Failed.id)
                    )
                  
                case Failure(exception) =>
                  log.error(
                    s"Could not retrieve file string $exception, for the request $req"
                  )
                  req.replyTo !  
                    KabutoApiHttpResponse(
                              status = HttpResponseStatus.Failed,
                              description = "Could not retrieve file string",
                              code = Some(HttpResponseStatus.Failed.id)
                            )

                case _ =>
                  req.replyTo !
                    KabutoApiHttpResponse(
                      status = HttpResponseStatus.Failed,
                      description = "Could not retrive file. Please try again",
                      code = Some(HttpResponseStatus.Failed.id)
                    )
                  

              }

            case Failure(exception) =>
              log.error(
                s"Could not retieve document metadata $exception, for the request $req"
              )
              req.replyTo !  
               KabutoApiHttpResponse(
                              status = HttpResponseStatus.Failed,
                              description = "Could not retrieve  document metadata",
                              code = Some(HttpResponseStatus.Failed.id)
                            )

            case _ =>
              req.replyTo !  
                  KabutoApiHttpResponse(
                              status = HttpResponseStatus.Failed,
                              description =  "Error retrieving document metadata, please try again later.",
                              code = Some(HttpResponseStatus.Failed.id)
                            )

          }

          Behaviors.same

        case req: DeleteDocument =>
          //delete file on disk first
          fileManagerServiceActor.ask(DeleteFile(req.filePath, _)) onComplete {
            case Success(FileProcessOK) =>
              //file deleted from disk, now delete its metadata
              documentMetadataDbService.ask(
                DeleteDocumentMetadata(req.docId, _)
              ) onComplete {
                case Success(DocumentMetadataProcessed) =>
                  req.replyTo ! 
                    KabutoApiHttpResponse(
                      status = HttpResponseStatus.Success,
                      description = "Document deleted",
                      code = Some(HttpResponseStatus.Success.id)
                    )
                  
                case Success(DocumentMetadataFailure(reason)) =>
                  req.replyTo ! 
                    KabutoApiHttpResponse(
                      status = HttpResponseStatus.Failed,
                      description = reason,
                      code = Some(HttpResponseStatus.Failed.id)
                    
                  )
                case Failure(error) =>
                  req.replyTo !  
                   KabutoApiHttpResponse(
                              status = HttpResponseStatus.Failed,
                              description =  "Error deleting document metadata, please try again later.",
                              code = Some(HttpResponseStatus.Failed.id)
                            )
                case _ =>
                  req.replyTo ! 
                    KabutoApiHttpResponse(
                      status = HttpResponseStatus.Failed,
                      description =
                        "Unknown error occurred during metdata deletion, please try again later",
                      code = Some(HttpResponseStatus.Failed.id)
                    
                  )

              }

            case Failure(error) =>
              req.replyTo !  
                 KabutoApiHttpResponse(
                              status = HttpResponseStatus.Failed,
                              description =  "Error deleting file from disk, please try again later.",
                              code = Some(HttpResponseStatus.Failed.id)
                            )

            case _ =>
              req.replyTo !  
                KabutoApiHttpResponse(
                  status = HttpResponseStatus.Failed,
                  description = "Could not delete file from disk",
                  code = Some(HttpResponseStatus.Failed.id)
                )
              
          }
          Behaviors.same




             case req: GetAllDocumentsMetadataByFileNumberCommand =>
          documentMetadataDbService.ask(
            FetchAllMetadataByFileNumberCommand(req.fileNumber, _)
          ) onComplete {
            case Success(
                  DocumentMetadataRetrieved(docMetadataList)
                ) =>  
              req.replyTo !  
                KabutoApiHttpResponse(
                  status = HttpResponseStatus.Success,
                  description = "List of all document metadata by file number",
                  code = Some(HttpResponseStatus.Success.id),
                  data = Some(docMetadataList.map(
                  metaData => DocumentMetaDataPayload(
                       fileNumber= metaData.fileNumber,
                      fileType = metaData.fileType,
                      title = metaData.title,
                      capturedAt = metaData.capturedAt,
                      updatedAt = metaData.updatedAt,
                      createdBy = metaData.createdBy,
                      updatedBy = metaData.updatedBy
                  )
                  ).toJson
                  )
                
              )
          

            case Failure(exception) =>
              log.error(
                s"Could not fetch all document meta data $exception, for the request $req"
              )
              req.replyTo !  
               KabutoApiHttpResponse(
                              status = HttpResponseStatus.Failed,
                              description = "Could not fetch all document metadata by file number",
                              code = Some(HttpResponseStatus.Failed.id)
                            )

          }

          Behaviors.same


      }

    }

}
