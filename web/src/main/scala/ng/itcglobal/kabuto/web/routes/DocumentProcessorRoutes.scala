package ng.itcglobal.kabuto
package web.routes

import java.util.UUID

import scala.concurrent.duration.DurationInt
import scala.concurrent.Future
import scala.util.{Failure, Success}

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout

import ng.itcglobal.kabuto._
import dms.{DocumentProcessorService, JsonSupport}

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
                    case DocumentProcessorService.DataResponse(dataResponse) =>
                      complete(dataResponse)
                    case DocumentProcessorService.ErrorResponse(msg) =>
                      failWith(throw new Exception (msg))
                    case _ =>
                      failWith(throw new Exception( "Unknown error occured"))
                  }
                case Failure(exception) =>
                  failWith(throw new Exception("Unable to complete the request"))
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
                   case DocumentProcessorService.DataResponse(dataResponse) =>
                      complete(dataResponse)
                    case DocumentProcessorService.ErrorResponse(msg) =>
                      failWith(throw new Exception (msg))
                    case _ =>
                      failWith( throw new Exception("Unknown error occured"))
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
