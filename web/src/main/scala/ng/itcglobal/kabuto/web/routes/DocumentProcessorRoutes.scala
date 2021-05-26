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

  import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
  implicit val timeout: Timeout = 3.seconds

  lazy val documentMetadataRoutes: Route = {
    pathPrefix("documents") {
      path(Segment) { docId =>
        concat(
          get {
            val futRes: Future[DocumentProcessorService.ProcessDocumentResponse] =
              documentProcessorService.ask(DocumentProcessorService.GetDocument(docId, _))
            onComplete(futRes) {
              case Success(document) =>
                complete(document)
              case Failure(exception) =>
                complete(StatusCodes.NotFound -> s"unable to complete the request $exception")
            }
          },
          post {
            entity(as[DocumentProcessorService.Document]) { doc =>

              val futRes: Future[DocumentProcessorService.ProcessDocumentResponse] =
                documentProcessorService.ask(DocumentProcessorService.AddDocument(doc, _))

              onComplete(futRes) {
                case Success(result) =>
                  result match {
                    case DocumentProcessed(value) =>
                      complete(value)
                    case DocumentProcessingFailed(reason) =>
                      complete(StatusCodes.NotFound -> reason)
                    case _ =>
                      complete(StatusCodes.InternalServerError -> "Unknown error!")
                  }
                case Failure(exception) =>
                  complete(StatusCodes.NotFound -> s"unable to complete the request $exception")
              }
            }
          }
        )
      }
    }

  }
}
