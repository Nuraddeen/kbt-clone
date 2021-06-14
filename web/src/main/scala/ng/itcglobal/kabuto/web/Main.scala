package ng.itcglobal.kabuto
package web

import scala.util.{Failure, Success}

import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteConcatenation.concat


import ng.itcglobal.kabuto._
import dms.DocumentProcessorService
import dms.FileManagerService
import core.db.postgres.services.DocumentMetadataDbService
import web.routes.DocumentProcessorRoutes

object Server {

  sealed trait Message 
  private final case class StartFailed(cause: Throwable) extends Message 
  private final case class Started(binding: ServerBinding) extends Message 
  case object Stop extends Message 
  
  def apply(host: String, port: Int): Behavior[Message] = Behaviors.setup { ctx => 

    implicit val system = ctx.system

    val fileManagerService        = ctx.spawn(FileManagerService(), "fileManagerServiceActor")
    val metadataService           = ctx.spawn(DocumentMetadataDbService(), "metadataServiceActor")
    val documentProcessorService  = ctx.spawn(DocumentProcessorService(metadataService, fileManagerService), "documentProcessingService")
    
    val documentProcessorRoute  = new DocumentProcessorRoutes(documentProcessorService).documentMetadataRoutes

    val route = concat(
      documentProcessorRoute
    )

    val serverBinding = Http().newServerAt(host, port).bind(route)

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
   ActorSystem(Server.apply("localhost", 8090), "BuildJobsServer")
 }
}