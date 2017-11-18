package edge.labs.orchestrator

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.dispatch.MessageDispatcher
import akka.stream.ActorMaterializer
import akka.util.Timeout
import scala.concurrent.duration._

/**
 * Container object for the ActorSystem and other Akka-related objects
 */
object Akka {

  implicit val system = ActorSystem("orchestrator")
  implicit val materializer = ActorMaterializer()

  // For Future invocation
  implicit val timeout = Timeout(10.seconds)

  /**
   * Create a Root-level named Actor
   *
   * @param props Props
   * @param name String
   * @return ActorRef
   */
  def createRootActor(props: Props, name: String): ActorRef = system.actorOf(props, name)

  /**
   * Fetch a MessageDispatcher definition that has been defined in application.conf
   *
   * @param key String
   * @return MessageDispatcher
   */
  def getDispatcher(key: String): MessageDispatcher = system.dispatchers.lookup(key)

}
