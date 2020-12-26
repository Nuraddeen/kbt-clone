package ng.itcglobal.hinata
package web

import scala.util.{ Success, Failure }

import akka.actor.typed.{ActorRef, ActorSystem, Behavior,  PostStop}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.Http

import ng.itcglobal.hinata._

import job.JobRepository

import web.routes.JobRoutes


object Server {

  sealed trait Message 
  private final case class StartFailed(cause: Throwable) extends Message 
  private final case class Started(binding: ServerBinding) extends Message 
  case object Stop extends Message 
  
  def apply(host: String, port: Int): Behavior[Message] = Behaviors.setup { ctx => 

    implicit val system = ctx.system

    val buildJobRepository = ctx.spawn(JobRepository(), "jobRepository")
    val route              = new JobRoutes(buildJobRepository)

    val serverBinding = Http().newServerAt(host, port).bind(route.jobRoutes)

    ctx.pipeToSelf(serverBinding) {
      case Success(binding) => Started(binding)
      case Failure(ex)      => StartFailed(ex)
    }

    def running(binding: ServerBinding): Behavior[Message] = 
      Behaviors.receiveMessagePartial[Message] {
        case Stop => 
          ctx.log.info(
            "Stopping server http://{}:{}", binding.localAddress.getHostString, binding.localAddress.getPort
          )
          Behaviors.stopped
      }.receiveSignal{
        case (_, PostStop) => 
          binding.unbind()
          Behaviors.same
      }

    def starting(wasStopped: Boolean): Behaviors.Receive[Message] =
      Behaviors.receiveMessage[Message] {
        case StartFailed(cause) => 
          throw new RuntimeException("Server failed to start", cause)
        case Started(binding) => 
          ctx.log.info(
            "Server online at http://{}:{}", binding.localAddress.getHostString, binding.localAddress.getPort
          )

          if(wasStopped) ctx.self ! Stop 
          running(binding)

          case Stop => 
            starting(wasStopped = true)
      }

      starting(wasStopped = false)
  }


def main(args: Array[String]): Unit = {
  val system: ActorSystem[Server.Message] = ActorSystem(Server("localhost", 8090), "BuildJobsServer")
 }
}