package ng.itcglobal.hinata
package web.routes

import scala.concurrent.duration._ 
import scala.concurrent.Future

import akka.actor.typed.{ActorRef, ActorSystem }
import akka.util.Timeout

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._ 
import akka.http.scaladsl.server.Route

import ng.itcglobal.hinata._ 

import job.{JobRepository, JsonSupport }

class   JobRoutes(buildJobRepository: ActorRef[JobRepository.Command])(implicit system: ActorSystem[_]) extends JsonSupport {

  import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
  import akka.actor.typed.scaladsl.AskPattern.Askable

  implicit val timeout: Timeout = 3.seconds

  lazy val jobRoutes: Route = {
    pathPrefix("jobs"){
      concat(
        get{
          complete(JobRepository.Job(1L, "men", JobRepository.Successful, 3L))
        },
        pathEnd{
          concat(
            post{
              entity(as[JobRepository.Job]) { job => 
                val operationPerformed = buildJobRepository.ask(JobRepository.AddJob(job, _))
                onSuccess(operationPerformed) {
                  case JobRepository.OK         => complete("Job added")
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