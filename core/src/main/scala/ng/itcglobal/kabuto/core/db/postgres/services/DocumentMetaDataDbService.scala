package ng.itcglobal.kabuto
package core.db.postgres.services

import java.time.LocalDateTime
import java.util.UUID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import ng.itcglobal.kabuto._
import core.db.postgres.DatabaseContext
import core.db.postgres.Tables.DocumentMetadata

object DocumentMetadataDbService extends DatabaseContext {
  import doobie.implicits._
  import quillContext._

  sealed trait DocumentMetadataCommand
  case class SaveDocumentMetadata(
      document: DocumentMetadata,
      replyTo: ActorRef[DocumentMetadataResponse]
  ) extends DocumentMetadataCommand {
    def runQuery: Future[Long] =
      run(query[DocumentMetadata]
          .insert(lift(document))
          )
        .transact(xa)
        .unsafeToFuture()
  }
  case class RetrieveDocumentMetadata(
      fileNumber: String,
      fileType: String,
      replyTo: ActorRef[DocumentMetadataResponse]
  ) extends DocumentMetadataCommand {
    def runQuery: Future[List[DocumentMetadata]] =
      run(
        query[DocumentMetadata]
          .filter(meta=> meta.fileNumber.equals(lift(fileNumber)) && meta.fileType.equals(lift(fileType)))
      ).transact(xa)
        .unsafeToFuture()
  }

  case class GetAllDocumentsMetadata(
      replyTo: ActorRef[DocumentMetadataResponse]
  ) extends DocumentMetadataCommand {
    def runQuery: Future[List[DocumentMetadata]] =
      run(query[DocumentMetadata])
        .transact(xa)
        .unsafeToFuture()
  }

  case class GetDocumentMetadataCount(
      replyTo: ActorRef[DocumentMetadataResponse]
  ) extends DocumentMetadataCommand {
    def runQuery: Future[Int] =
      sql"""SELECT count(*) FROM document_metadata;"""
        .query[Int]
        .unique
        .transact(xa)
        .unsafeToFuture()
  }

  case class DeleteDocumentMetadata(
      docId: UUID,
      replyTo: ActorRef[DocumentMetadataResponse]
  ) extends DocumentMetadataCommand {
    def runQuery: Future[Long] =
      run(
        query[DocumentMetadata]
          .filter(_.id.equals(lift(docId)))
          .delete
      ).transact(xa)
        .unsafeToFuture()
  }


     case class FlagMetadataAsDuplicate(
       excludeId: UUID,
       fileNumber: String,
       fileType: String,
       replyTo: ActorRef[DocumentMetadataResponse]
   ) extends DocumentMetadataCommand {
     def runQuery: Future[Long] =
       run(
         query[DocumentMetadata] 
            .filter(meta => !meta.id.equals(lift(excludeId)) 
                        && meta.fileNumber.equals(lift(fileNumber)) 
                        && meta.fileType.equals(lift(fileType))
                  )
          .update(_.status -> Some("duplicate"))
       ).transact(xa)
         .unsafeToFuture()
   }

  
  sealed trait DocumentMetadataResponse
  case class DocumentMetadataSaved(documentId: UUID)
      extends DocumentMetadataResponse
  case class AllDocumentsMetadataCount(count: Int)
      extends DocumentMetadataResponse
  case class AllDocumentsMetadata(metadataDocuments: Seq[DocumentMetadata])
      extends DocumentMetadataResponse
  case class DocumentMetadataRetrieved(docMetadataList: List[DocumentMetadata])
      extends DocumentMetadataResponse
  case class DocumentMetadataFailure(reason: String)
      extends DocumentMetadataResponse
  case object DocumentMetadataProcessed extends DocumentMetadataResponse

  def apply(): Behavior[DocumentMetadataCommand] =
    Behaviors.receive { (context, message) =>
      val log = context.log

      message match {
        case req: SaveDocumentMetadata =>
          val futResult: Future[Long] = req.runQuery

          futResult onComplete {
            case Success(1) => 
                  log.info("Document successfully saved {}", message)
                  req.replyTo ! DocumentMetadataSaved(req.document.id)
            case Success(_) =>
                  log.error("Unable to save document {}", message)
                  req.replyTo ! DocumentMetadataFailure(
                    s"Unable to save document $message"
                  )
             
            case Failure(exception) =>
              log.error(
                "Unable to complete the request {}, {}",
                message,
                exception
              )
              req.replyTo ! DocumentMetadataFailure(exception.getMessage)
          }

          Behaviors.same

        case req: RetrieveDocumentMetadata =>
          val futResult: Future[List[DocumentMetadata]] = req.runQuery

          futResult onComplete {
            case Success(docMetadataList) =>
              req.replyTo ! DocumentMetadataRetrieved(docMetadataList)
            case Failure(exception) =>
              log.error(
                s"Could not retrieve document meta data {$exception}, request $req"
              )
              req.replyTo ! DocumentMetadataFailure(
                "Could not retrieve document meta data"
              )
          }

          Behaviors.same

        case req: GetAllDocumentsMetadata =>
          val futResult = req.runQuery

          futResult onComplete {
            case Success(docsList) =>
              req.replyTo ! AllDocumentsMetadata(docsList)
              Behaviors.same

            case Failure(exception) =>
              log.error(
                s"Could not retrieve all documents metadata {$exception}, request $req"
              )
              req.replyTo ! DocumentMetadataFailure(
                "Could not retrieve all documents metadata"
              )
          }

          Behaviors.same

        case req: GetDocumentMetadataCount =>
          req.runQuery
            .onComplete {
              case Success(count) =>
                req.replyTo ! AllDocumentsMetadataCount(count)
              case Failure(exception) =>
                log.error(
                  s"Could not retrieve documents metadata count {$exception}, request $req"
                )
                req.replyTo ! DocumentMetadataFailure(
                  "Could not retrieve documents metadata count"
                )
            }

          Behaviors.same

        case req: DeleteDocumentMetadata =>
          req.runQuery
            .onComplete {
              case Success(count) => req.replyTo ! DocumentMetadataProcessed
              case Failure(exception) =>
                log.error(
                  s"Could not delete document metadata {$exception}, request $req"
                )
                req.replyTo ! DocumentMetadataFailure(
                  "Could not delete document metadata"
                )
            }

          Behaviors.same


          case req: FlagMetadataAsDuplicate =>
                    req.runQuery
                      .onComplete {
                        case Success(count) => req.replyTo ! DocumentMetadataProcessed
                        case Failure(exception) =>
                          log.error(
                            s"Could not flag meta data as duplicate {$exception}, request $req"
                          )
                          req.replyTo ! DocumentMetadataFailure(
                            "Could not flat metadata as duplicate"
                          )
                      }

                    Behaviors.same
      }
    }
}
