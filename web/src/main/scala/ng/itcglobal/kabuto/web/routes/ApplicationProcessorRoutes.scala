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
      // pathPrefix("documents") {
      // concat(
      //   path(Segment) { docId =>          
      //     get {
      //       val futRes: Future[ApplicationProcessorService.ProcessDocumentResponse] =
      //         documentProcessorService.ask(ApplicationProcessorService.GetDocument(docId, _))
            
      //         onComplete(futRes) {
      //           case Success(document) =>
      //             document match {
      //               case ApplicationProcessorService.GetDocumentResponse(doc) =>
      //                 complete(doc)
      //               case DocumentProcessingFailed(reason) =>
      //                 complete(StatusCodes.NotFound -> reason)
      //               case _ =>
      //                 complete(StatusCodes.InternalServerError -> "unknown error")
      //             }
      //             complete(document)
      //           case Failure(exception) =>
      //             failWith(exception)
      //             // complete(StatusCodes.NotFound -> s"unable to complete the request ${exception}")
      //       }
      //     }
      //   },
        // post {
        //   entity(as[ApplicationProcessorService.Document]) { doc =>

        //     val futRes: Future[ApplicationProcessorService.ProcessDocumentResponse] =
        //       documentProcessorService.ask(ApplicationProcessorService.AddDocument(doc, _))

        //     onComplete(futRes) {
        //       case Success(result) =>
        //         result match {
        //           case DocumentProcessed(value) =>
        //             complete(value)
        //           case DocumentProcessingFailed(reason) =>
        //             complete(StatusCodes.BadRequest -> reason)
        //           case _ =>
        //             complete(StatusCodes.InternalServerError -> "Unknown error!")
        //         }
        //       case Failure(exception) =>
        //         complete(StatusCodes.NotFound -> s"unable to complete the request $exception")
        //     }
        //   }
        // }
      // )
      // },
      pathPrefix("applications") {
        concat(
          path(Segment) { applicationId: String =>
            get {
              val futFileResponse: Future[FileManagerService.FileResponse] = fileMangerService.ask(FileManagerService.GetFilesByPath(applicationId, _))

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
              val futRes: Future[FileManagerService.FileResponse] = fileMangerService.ask(FileManagerService.GetAllDirectories(_))

              onComplete(futRes) {
                case Success(filesRes) =>
                  filesRes match {
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
          }
        )
      }
    )
  }
}
