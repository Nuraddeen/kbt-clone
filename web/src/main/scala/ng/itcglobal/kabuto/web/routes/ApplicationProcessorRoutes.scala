package ng.itcglobal.kabuto
package web.routes

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
import dms.{FileManagerService, JsonSupport}

class ApplicationProcessorRoutes(fileMangerService: ActorRef[FileManagerService.FileCommand])(implicit system: ActorSystem[_]) extends JsonSupport {

  implicit val timeout: Timeout = 10.seconds

  lazy val documentMetadataRoutes: Route = {
    concat(  
      pathPrefix("applications") {
        concat(
          path(Segment / Segment) { (applicationId: String, documentName: String) =>
            get {
              val futFileResponse: Future[FileManagerService.Response] = fileMangerService
                .ask(FileManagerService.GetSingleDocumentFromApplication(s"$applicationId/$documentName", _))

              onComplete(futFileResponse) {
                case Success(fileResponse) =>
                  fileResponse match {
                    case FileManagerService.SingleFileSearchResponse(documentImage) =>
                      complete(documentImage)
                    case FileManagerService.FileResponseError(errors) =>
                      complete(StatusCodes.InternalServerError -> s"$errors")
                    case _                                            =>
                      failWith(new Exception("unkwon error happened."))
                  }
                case Failure(error) => failWith(error)
              }
            }
          },
          path(Segment) { applicationId: String =>
            get {
              val futFileResponse: Future[FileManagerService.Response] = fileMangerService.ask(FileManagerService.GetFilesByPath(applicationId, _))

              onComplete(futFileResponse) {
                case Success(fileResponse) =>
                  fileResponse match {
                    case FileManagerService.FileSearchResponse(files) =>
                      complete(files)
                    case FileManagerService.FileResponseError(errors) =>
                      complete(StatusCodes.InternalServerError -> s"$errors")
                    case _                                            =>
                      failWith(new Exception("unkwon error happened."))
                  }
                case Failure(error) => failWith(error)
              }
            }
          },
          pathEndOrSingleSlash {
            get {
              val futRes: Future[FileManagerService.Response] = fileMangerService.ask(FileManagerService.GetAllDirectories(_))

              onComplete(futRes) {
                case Success(filesRes) =>
                  filesRes match {
                    case FileManagerService.AllFilesFetchedResponse(files) =>
                      complete(files)
                    case FileManagerService.FileResponseError(errors) =>
                      complete(StatusCodes.InternalServerError -> s"$errors")
                    case _                                            =>
                      failWith(new Exception("unkwon error happened."))
                  }
                case Failure(error) => failWith(error)
              }
            }
          }
        )
      }
    )
  }
}
