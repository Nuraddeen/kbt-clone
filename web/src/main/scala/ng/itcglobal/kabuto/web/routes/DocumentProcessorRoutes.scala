package ng.itcglobal.kabuto
package web.routes

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.Future
import scala.util.{Failure, Success}

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout

import ng.itcglobal.kabuto._
import dms.{DocumentProcessorService, JsonSupport}
import dms.DocumentProcessorService.{DocumentProcessed, DocumentProcessingFailed}

class DocumentProcessorRoutes(documentProcessorService: ActorRef[DocumentProcessorService.ProcessDocumentCommand])(implicit system: ActorSystem[_]) extends JsonSupport {

  implicit val timeout: Timeout = 10.seconds

  lazy val documentMetadataRoutes: Route = {
    pathPrefix("documents") {
      concat(
        path(Segment / Segment) { (fileNumber, fileType) =>          
          get {
            val futRes: Future[DocumentProcessorService.ProcessDocumentResponse] =
              documentProcessorService.ask(DocumentProcessorService.GetDocument(fileNumber, fileType, _))
            
              onComplete(futRes) {
                case Success(document) =>
                  document match {
                    case DocumentProcessorService.DocumentFound(doc) =>
                      complete(doc)
                    case DocumentProcessingFailed(reason) =>
                      complete(StatusCodes.NotFound -> reason)
                    case _ =>
                      complete(StatusCodes.InternalServerError -> "Unknown error occured")
                  }
                case Failure(exception) =>
                  failWith(throw new Exception("Unable to complete the request"))
                  // complete(StatusCodes.NotFound -> s"unable to complete the request ${exception}")
            }
          }
        },
        post {
          entity(as[DocumentProcessorService.DocumentDto]) { docDto =>

            val futRes: Future[DocumentProcessorService.ProcessDocumentResponse] =
              documentProcessorService.ask(DocumentProcessorService.AddDocument(docDto, _))

            onComplete(futRes) {
              case Success(result) =>
                result match {
                  case DocumentProcessed(value) =>
                    complete(value.toString)
                  case DocumentProcessingFailed(reason) =>
                    complete(StatusCodes.BadRequest -> reason)
                  case _ =>
                    complete(StatusCodes.InternalServerError -> "Unknown error occured!")
                }
              case Failure(exception) =>
                failWith(throw new Exception("Unable to complete the request"))
            }
          }
        }
      )
    }
  }
}
