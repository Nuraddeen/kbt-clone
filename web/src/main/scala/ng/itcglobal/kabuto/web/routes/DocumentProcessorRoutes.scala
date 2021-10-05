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

class DocumentProcessorRoutes(documentProcessorService: ActorRef[DocumentProcessorService.Command])(implicit system: ActorSystem[_]) extends JsonSupport {

  implicit val timeout: Timeout = 10.seconds

  lazy val documentMetadataRoutes: Route = {
    pathPrefix("documents") {
      concat(
        path(Segment / Segment) { (fileNumber, fileType) =>          
          get {
            val futRes =
              documentProcessorService.ask(DocumentProcessorService.GetDocument(fileNumber, fileType, _))
            
              onComplete(futRes) {
                case Success(response) =>  complete(response)
                case Failure(exception) => failWith(throw new Exception("Unable to complete the request"))
            }
          }
        },
        post {
          entity(as[DocumentProcessorService.DocumentDto]) { docDto =>

            val addDocument =
              documentProcessorService.ask(DocumentProcessorService.AddDocument(docDto, _))

            onComplete(addDocument) {
              case Success(response) =>  complete(response)
              case Failure(exception) => failWith(throw new Exception("Unable to complete the request"))
            }
          }
        }
      )
    }
  }
}
