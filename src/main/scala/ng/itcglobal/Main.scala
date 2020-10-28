package ng.itcglobal

import java.util.UUID

import scala.util.Failure
import scala.util.Success
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

object Main {

  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    import system.executionContext

    Http()
    .newServerAt("localhost", 8080)
    .bind(routes)
    .onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)

      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {

    val rootBehavior = Behaviors.setup[Nothing] { context =>
      // val domainActor = context.spawn(DomainPersistentActor(UUID.randomUUID()), "DomainActorRef")
      // context.watch(domainActor)

      // val routes = new DomainRoutes(domainActor)(context.system)
      // startHttpServer(routes.domainRoutes)(context.system)

      Behaviors.empty
    }
    
    ActorSystem[Nothing](rootBehavior, "TicketHttpServer")

  }
}
