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


object DocumentMetadataDbService extends DatabaseContext {
  import doobie.implicits._
  import quillContext._

  sealed trait MetaCommand
  case class SaveDocument(document: DocumentMetadata, replyTo: ActorRef[FileMetaResponse]) extends MetaCommand {
    def runQuery: Future[Long] =
      run(query[DocumentMetadata].insert(lift(document)))
        .transact(xa)
        .unsafeToFuture
  }
  case class RetrieveDocument(documentId: UUID, replyTo: ActorRef[FileMetaResponse]) extends MetaCommand {
    def runQuery: Future[Option[DocumentMetadata]] =
      run(query[DocumentMetadata].filter(_.id.equals(lift(documentId))))
        .transact(xa)
        .map(_.headOption)
        .unsafeToFuture
  }

  case class GetAllDocumentsMetadata(replyTo: ActorRef[FileMetaResponse]) extends MetaCommand {
    def runQuery: Future[List[DocumentMetadata]] =
      run(query[DocumentMetadata])
        .transact(xa)
        .unsafeToFuture()
  }

  case class GetMetaDocumentCount(replyTo: ActorRef[FileMetaResponse]) extends MetaCommand {
      def runQuery: Future[Int] =
        sql"""SELECT count(*) FROM document_metadata;"""
          .query[Int]
          .unique
          .transact(xa)
          .unsafeToFuture()
    }

  sealed trait FileMetaResponse
  case class FileMetaSavedSuccessfully(documentId: UUID) extends FileMetaResponse
  case class AllMetadataCount(count: Int) extends FileMetaResponse
  case class AllDocumentsMetadata(metadataDocuments: Seq[DocumentMetadata]) extends FileMetaResponse
  case class FileMetaRetrieved(fileMeta: DocumentMetadata) extends FileMetaResponse
  case class FileMetaFailureResponse(reason: String) extends FileMetaResponse

  def apply(): Behavior[MetaCommand] = Behaviors.receive { (context, message) =>
      val log = context.log

      message match {
        case req: SaveDocument =>
          val futResult: Future[Long] = req.runQuery

            futResult onComplete {
              case Success(result) =>
                if (result == 1) {
                  log.info("Document successfully saved {}", message)
                  req.replyTo ! FileMetaSavedSuccessfully(req.document.id)
                } else {
                  log.error("Unable to save document {}", message)
                  req.replyTo ! FileMetaFailureResponse(s"Unable to save document $message")
                }

              case Failure(exception) =>
                log.error("Unable to complete the request {}, {}", message, exception)
                req.replyTo ! FileMetaFailureResponse(exception.getMessage)
            }

          Behaviors.same

        case req: RetrieveDocument =>
          val futResult: Future[Option[DocumentMetadata]] = req.runQuery

          futResult onComplete {
            case Success(optFilePath) =>
              optFilePath match {
                case Some(fileMeta) =>
                  req.replyTo ! FileMetaRetrieved(fileMeta)
                case None =>
                  req.replyTo ! FileMetaFailureResponse(
                    s"File with the id ${req.documentId} doesn't exists."
                  )
              }

            case Failure(exception) =>
              req.replyTo ! FileMetaFailureResponse(exception.getMessage)
          }

          Behaviors.same

        case req: GetAllDocumentsMetadata =>
          val futResult = req.runQuery

          futResult onComplete {
            case Success(docsList) =>
              req.replyTo ! AllDocumentsMetadata(docsList)
              Behaviors.same

            case Failure(exception) =>
              req.replyTo ! FileMetaFailureResponse(exception.toString)
          }

          Behaviors.same

        case req: GetMetaDocumentCount =>
           req
             .runQuery
             .onComplete {
                case Success(count) => req.replyTo ! AllMetadataCount(count)
                case Failure(exception) => req.replyTo ! FileMetaFailureResponse(exception.toString)
             }

          Behaviors.same
      }
    }
}
