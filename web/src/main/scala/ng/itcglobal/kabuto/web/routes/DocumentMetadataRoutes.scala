package ng.itcglobal.kabuto
package web.routes

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.Future

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout

import ng.itcglobal.kabuto._
import core.db.postgres.services.DocumentMetadataDbService._
import dms.JsonSupport

class DocumentMetadataRoutes(documentDbService: ActorRef[Command])(implicit system: ActorSystem[_]) extends JsonSupport {

  import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
  import akka.actor.typed.scaladsl.AskPattern.Askable

  implicit val timeout: Timeout = 3.seconds

  lazy val documentMetadataRoutes: Route = {
    pathPrefix("documents") {
      path(Segment) { docId =>
        get {
          val futRes: Future[Response] = documentDbService.ask(RetrieveDocument(UUID.fromString(docId), _))
          onComplete(futRes) {
            case SuccessResponse(value) => complete(value)
            case FailureResponse(reason) => complete(StatusCodes.NotFound -> reason)
          }
        }
      }
    }

  }
}
