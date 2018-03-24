package edge.labs.orchestrator

import akka.actor.{Actor, ActorSystem, Props, UnhandledMessage}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestActorRef, TestKitBase, TestProbe}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.reflect.ClassTag

/* @author medge */

/**
 * Common Actor Testing utilities/traits
 */
trait BaseActorTest extends BaseTest
  with TestKitBase with DefaultTimeout with ImplicitSender
  with ReflectionHelper
  with TheTestReaper {

  implicit lazy val system = ActorSystem(
    "OrchestratorTest",
    ConfigFactory.load().withFallback(
      ConfigFactory.parseString(
        """|
          |akka {
          |    loggers = ["akka.testkit.TestEventListener"]
          |    loglevel = "DEBUG"
          |    debug {
          |        # enable DEBUG logging of actor lifecycle changes
          |        lifecycle = on
          |
          |        # enable DEBUG logging of unhandled messages
          |        unhandled = on
          |    }
          |}
          |""".stripMargin
      )
    )
  )

  /**
   * Create a TestActorRef from the given Props object
   *
   * @param props Props
   * @return TestActorRef
   */
  def testActor(props: Props) = TestActorRef(props)

  /**
   * A variant of testActor(props) that gives the Actor a more meaningful name to identify it
   * in the logs. Must be careful to ensure the name is unique!
   *
   * @param props Props
   * @param name String
   * @return TestActorRef
   */
  def testActor(props: Props, name: String) = TestActorRef(props, name)



  /***********************************************************
   *
   *                Event Stream Helpers
   *
   **********************************************************/

  val eventStreamProbe = TestProbe()

  // Auto-subscribe eventStreamProbe
  system.eventStream.subscribe(eventStreamProbe.ref, classOf[Event])

  /**
   * Expect that a message was published to the event stream. Ignores any other messages that
   * have potentially been published.
   *
   * @param obj T expected message
   * @tparam T Message type
   * @return T
   */
  def expectEventStreamMessage[T : ClassTag](obj: T) = eventStreamProbe.fishForMessage(3.seconds, classNameOf[T]) {
    case expected: T => true
    case _ => false
  }

  /**
   * Expect that the given message, when sent to the given TestActorRef,
   * was Unhandled (a.k.a an UnhandledMessage is published by Akka to the Event Stream).
   *
   * @param message T Message to send to the Actor which should be unhandled
   * @param ref TestActorRef Actor who will be sent the given message
   */
  def expectUnhandled[T : ClassTag, A <: Actor](message: T, ref: TestActorRef[A]): Unit = {
    // Setup TestProbe to listen for the message
    val listener = TestProbe()
    system.eventStream.subscribe(listener.ref, classOf[UnhandledMessage])

    ref.receive(message)

    listener.expectMsg(1.second, UnhandledMessage(message, system.deadLetters, ref))

    system.eventStream.unsubscribe(listener.ref)
  }

}
