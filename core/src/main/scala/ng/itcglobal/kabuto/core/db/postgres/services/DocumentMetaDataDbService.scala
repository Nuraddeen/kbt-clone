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

  sealed trait DocumentMetadataCommand
  case class SaveDocumentMetadata(document: DocumentMetadata, replyTo: ActorRef[DocumentMetadataResponse]) extends DocumentMetadataCommand {
    def runQuery: Future[Long] =
      run(query[DocumentMetadata].insert(lift(document)))
        .transact(xa)
        .unsafeToFuture
  }
  case class RetrieveDocumentMetadata(documentId: UUID, replyTo: ActorRef[DocumentMetadataResponse]) extends DocumentMetadataCommand {
    def runQuery: Future[Option[DocumentMetadata]] =
      run(query[DocumentMetadata].filter(_.id.equals(lift(documentId))))
        .transact(xa)
        .map(_.headOption)
        .unsafeToFuture
  }

  case class GetAllDocumentsMetadata(replyTo: ActorRef[DocumentMetadataResponse]) extends DocumentMetadataCommand {
    def runQuery: Future[List[DocumentMetadata]] =
      run(query[DocumentMetadata])
        .transact(xa)
        .unsafeToFuture()
  }

  case class GetDocumentMetadataCount(replyTo: ActorRef[DocumentMetadataResponse]) extends DocumentMetadataCommand {
      def runQuery: Future[Int] =
        sql"""SELECT count(*) FROM document_metadata;"""
          .query[Int]
          .unique
          .transact(xa)
          .unsafeToFuture()
    }

  sealed trait DocumentMetadataResponse
  case class DocumentMetadataSaved(documentId: UUID) extends DocumentMetadataResponse
  case class AllDocumentsMetadataCount(count: Int) extends DocumentMetadataResponse
  case class AllDocumentsMetadata(metadataDocuments: Seq[DocumentMetadata]) extends DocumentMetadataResponse
  case class DocumentMetadataRetrieved(docMetadata: DocumentMetadata) extends DocumentMetadataResponse
  case class DocumentMetadataFailure(reason: String) extends DocumentMetadataResponse

  def apply(): Behavior[DocumentMetadataCommand] = Behaviors.receive { (context, message) =>
      val log = context.log

      message match {
        case req: SaveDocumentMetadata =>
          val futResult: Future[Long] = req.runQuery

            futResult onComplete {
              case Success(result) =>
                 result match {
                   case 1 =>                   
                      log.info("Document successfully saved {}", message)
                      req.replyTo ! DocumentMetadataSaved(req.document.id)
                   case _ =>
                      log.error("Unable to save document {}", message)
                    req.replyTo ! DocumentMetadataFailure(s"Unable to save document $message")
                 }
              case Failure(exception) =>
                log.error("Unable to complete the request {}, {}", message, exception)
                req.replyTo ! DocumentMetadataFailure(exception.getMessage)
            }

          Behaviors.same

        case req: RetrieveDocumentMetadata =>
          val futResult: Future[Option[DocumentMetadata]] = req.runQuery

          futResult onComplete {
            case Success(optFilePath) =>
              optFilePath match {
                case Some(fileMeta) =>
                  req.replyTo ! DocumentMetadataRetrieved(fileMeta)
                case None =>
                  req.replyTo ! DocumentMetadataFailure(
                    s"File with the id ${req.documentId} doesn't exists."
                  )
              }

            case Failure(exception) =>
              req.replyTo ! DocumentMetadataFailure(exception.getMessage)
          }

          Behaviors.same

        case req: GetAllDocumentsMetadata =>
          val futResult = req.runQuery

          futResult onComplete {
            case Success(docsList) =>
              req.replyTo ! AllDocumentsMetadata(docsList)
              Behaviors.same

            case Failure(exception) =>
              req.replyTo ! DocumentMetadataFailure(exception.toString)
          }

          Behaviors.same

        case req: GetDocumentMetadataCount =>
           req
             .runQuery
             .onComplete {
                case Success(count) => req.replyTo ! AllDocumentsMetadataCount(count)
                case Failure(exception) => req.replyTo ! DocumentMetadataFailure(exception.toString)
             }

          Behaviors.same
      }
    }
}
