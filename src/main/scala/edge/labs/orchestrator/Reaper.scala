package edge.labs.orchestrator

import akka.actor.{ActorRef, Terminated}
import edge.labs.orchestrator.actor.BaseActor

import scala.collection.mutable.ArrayBuffer

object Reaper {
  case class Watch(actor: ActorRef)
}

/**
 * Actor which watches the full system run
 */
class ProdReaper extends Reaper {

  override def allSoulsReaped(): Unit = {
    log.info("All Actors reaped. run complete")
  }

}

/**
 * Base definition of an Actor whose sole responsibility is to wait for the death of a particular
 * set of actors. Once this occurs the Actor System is shut down and the app terminates.
 */
abstract class Reaper extends BaseActor {

  import Reaper._

  // Keep track of what we're watching
  val watched: ArrayBuffer[ActorRef] = ArrayBuffer.empty[ActorRef]

  // Derivations need to implement this method.  It's the hook that's called when everything's dead
  def allSoulsReaped(): Unit

  final def receive: Receive = {

    // Watch the given Actor and wait for termination
    case Watch(ref) =>
      log.debug(s"Now watching ${ref.path}")
      context.watch(ref)
      watched += ref

    // Akka has signaled that a watched Actor has terminated
    case Terminated(ref) =>
      log.debug(s"${ref.path} terminated")
      watched -= ref
      if (watched.isEmpty) allSoulsReaped()

  }

}
