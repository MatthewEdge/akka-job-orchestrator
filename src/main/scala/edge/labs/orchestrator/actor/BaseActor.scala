package edge.labs.orchestrator.actor

import akka.actor.{Actor, ActorLogging, Scheduler}
import akka.dispatch.MessageDispatcher
import edge.labs.orchestrator.Settings

/**
 * Canonical Actor definition. Also provides some useful
 * helper methods and access to application Settings.
 *
 * Note: administrative tasks come in the form of *Interceptor traits
 * which MUST be mixed in AFTER the ReceivePipeline mixin
 *
 * @author medge
 */
trait BaseActor extends Actor with ActorLogging
  with EventActor
  with ReceivePipeline
  with LoggingInterceptor
{

  // Access to application-specific Settings
  val settings = Settings()

  // Quick access to the Akka Scheduler (or can be swapped if desired)
  val taskScheduler: Scheduler = context.system.scheduler

  // Fetch desired Dispatcher
  def getDispatcher(key: String): MessageDispatcher = context.system.dispatchers.lookup(key)

  // Stop the current actor and free it's resources
  def stop(): Unit = context.stop(self)

}
