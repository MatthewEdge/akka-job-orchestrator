package edge.labs.orchestrator.actor

import akka.actor.Actor
import akka.event.EventStream
import edge.labs.orchestrator.ReflectionHelper

import scala.reflect.ClassTag

/**
 * Helper methods for Actors who can publish/listen for messages on the Event Stream
 *
 * @author medge
 */
trait EventActor extends Actor with ReflectionHelper {

  // Grabs a reference to the Akka eventStream for pub/sub style messaging
  val eventStream: EventStream = context.system.eventStream

  // Subscribe the current Actor to event messages of the given type T published on the Event Stream
  def subscribeTo[T : ClassTag]: Boolean = eventStream.subscribe(self, runtimeClassOf[T])

  // Un-subscribe the current Actor to event messages of the given type T published on the Event Stream
  def unsubscribeFrom[T : ClassTag]: Boolean = eventStream.unsubscribe(self, runtimeClassOf[T])

  // Publish the given message to the Event Stream
  def publish(message: AnyRef): Unit = eventStream.publish(message)

}
