package edge.labs.orchestrator.actor

import akka.actor.ActorLogging
import edge.labs.orchestrator.actor.ReceivePipeline.Inner
import edge.labs.orchestrator.{Command, Event}

/**
 * Logging interceptor which logs Event messages being passed around to the DEBUG log level
 *
 * @author medge
 */
trait LoggingInterceptor extends ActorLogging {
  this: ReceivePipeline =>

    pipelineInner {

      // Catch and log Events
      case e: Event =>
        log.debug(s"$e event | To: ${this.self.path}")
        Inner(e)

      // Catch and log Commands
      case c: Command =>
        log.debug(s"$c command | To: ${this.self.path}")
        Inner(c)
    }

}
