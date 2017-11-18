package edge.labs.orchestrator.actor

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import edge.labs.orchestrator.BaseActorTest

import scala.concurrent.duration.FiniteDuration

/* @author medge */

/**
 * Unit Tests for the EventActor trait
 *
 * Note:
 *    Different case class Messages are used in this test so that tests do not cross. Since we are using the Event Stream
 *    messages published by one test could potentially be picked up by another and cause failures
 */
class EventActorTest extends BaseActorTest {

  // Expected Response to a TestEvent
  case class ExpectedResponse()

  it should "respond to messages received from the Event Stream after subscribing" in {

    // Test Events to listen for
    case class TestSubscribedEvent(from: ActorRef)

    class SubscribeActor extends EventActor {

      override def preStart(): Unit = {
        subscribeTo[TestSubscribedEvent]
      }

      override def receive: Receive = {
        case TestSubscribedEvent(from) => from ! ExpectedResponse()
      }
    }

    val instance = testActor(Props(new SubscribeActor))

    val stub = TestProbe()
    system.eventStream.publish(TestSubscribedEvent(stub.ref))

    stub.expectMsg(ExpectedResponse())
  }

  it should "not respond to messages on the Event Stream after unsubscribing" in {

    case class TestUnsubscribedEvent(from: ActorRef)

    class UnsubscribeActor extends EventActor {

      subscribeTo[TestUnsubscribedEvent] // Subscribe first to enact a Subscription

      override def preStart(): Unit = {
        unsubscribeFrom[TestUnsubscribedEvent] // Now try to unsubscribe
      }

      override def receive: Receive = {
        case TestUnsubscribedEvent(from) => from ! ExpectedResponse() // This should never happen
      }
    }

    val instance = testActor(Props(new UnsubscribeActor))

    val stub = TestProbe()
    system.eventStream.publish(TestUnsubscribedEvent(stub.ref))

    // We should not receive any reply
    stub.expectNoMessage(FiniteDuration(1, TimeUnit.SECONDS))
  }

}
