package ng.itcglobal.kabuto.web.routes

import java.io.File
import javax.imageio.ImageIO
import com.twelvemonkeys.contrib.tiff._
import com.twelvemonkeys.contrib.tiff.TIFFUtilities.TIFFPage

import scala.concurrent.duration._
import scala.concurrent.Future
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ng.itcglobal.kabuto._
import ng.itcglobal.kabuto.dms.{JobRepository, JsonSupport}

class   DmsRoutes(buildJobRepository: ActorRef[JobRepository.Command])(implicit system: ActorSystem[_]) extends JsonSupport {

  import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
  import akka.actor.typed.scaladsl.AskPattern.Askable

  implicit val timeout: Timeout = 3.seconds

  lazy val jobRoutes: Route = {
    pathPrefix("jobs"){
      concat(
        get{

          val m  = (new File("data/RES-2014-2368.tif"))
          val files = TIFFUtilities.split(m, new File("data/xxx"))

          complete(JobRepository.Job(1L, "men", JobRepository.Successful, 3L))
        },
        pathEnd{
          concat(
            post{
              entity(as[JobRepository.Job]) { job => 
                val operationPerformed = buildJobRepository.ask(JobRepository.AddJob(job, _))
                onSuccess(operationPerformed) {
                  case JobRepository.OK         => complete(StatusCodes.OK, "Job added")
                  case JobRepository.KO(reason) => complete(StatusCodes.InternalServerError -> reason)
                }
              }
            }, 
            delete {
              val op = buildJobRepository.ask(JobRepository.ClearJobs(_))
              onSuccess(op){
                case JobRepository.OK => complete("Jobs cleared")
                case JobRepository.KO(reason) => complete(StatusCodes.InternalServerError -> reason)
              }
            }
          )
        }, 
        (get & path(LongNumber)){ id => 
          val maybeJob = buildJobRepository.ask(JobRepository.GetJobById(id, _ ))
          rejectEmptyResponse{
             complete(maybeJob)
          }
        }
      )
    }
  }
}