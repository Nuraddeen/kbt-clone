package ng.itcglobal.kabuto
package core.db.postgres.services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import ng.itcglobal.kabuto._
import core.db.postgres.DatabaseContext

import java.util.UUID

object DocumentMetadataDbService extends DatabaseContext {
  import doobie.implicits._
  import quillContext._

  sealed trait Command
  case class SaveDocument(document: DocumentMetadata, replyTo: ActorRef[Response]) extends Command {
    def runQuery: Future[Long] = {
      run(query[DocumentMetadata].insert(lift(document)))
        .transact(xa)
        .unsafeToFuture
    }
  }

  case class RetrieveDocument(documentId: UUID, replyTo: ActorRef[Response]) extends Command {
    def runQuery: Future[Option[String]] = {
      sql"""SELECT file_path FROM document_metadata WHERE id = ${documentId.toString}::UUID"""
        .query[String]
        .option
        .transact(xa)
        .unsafeToFuture()
    }
  }

  sealed trait Response
  case class SuccessResponse(message: String) extends Response
  case class FailureResponse(reason: String) extends Response

  import quillContext._

  def apply(): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      {
        val log = context.log

        message match {
          case req: SaveDocument =>
            val futResult: Future[Long] = req.runQuery

              futResult onComplete {
                case Success(result) =>
                  if (result == 1) {
                    log.info("Document successfully saved {}", message)
                    req.replyTo ! SuccessResponse(req.document.id.toString)
                  } else {
                    log.error("Unable to save document {}", message)
                    req.replyTo ! FailureResponse(s"Unable to save document $message")
                  }

                case Failure(exception) =>
                  log.error("Unable to complete the request {}, {}", message, exception)
                  req.replyTo ! FailureResponse(exception.getMessage)
              }

            Behaviors.same

          case req: RetrieveDocument =>
            val futResult: Future[Option[String]] = req.runQuery

            futResult onComplete {
              case Success(optFilePath) =>
                optFilePath match {
                  case Some(filePath) =>
                    req.replyTo ! SuccessResponse(filePath)
                  case None =>
                    req.replyTo ! FailureResponse(
                      s"File with the id ${req.documentId} doesn't exists."
                    )
                }

              case Failure(exception) =>
                req.replyTo ! FailureResponse(exception.getMessage)
            }

            Behaviors.same
        }
      }
    }
}
