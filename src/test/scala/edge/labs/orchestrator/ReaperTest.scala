package edge.labs.orchestrator

import akka.actor.Props
import akka.testkit.TestProbe
import edge.labs.orchestrator.Reaper.Watch

/* @author medge */

/**
 * Unit Tests for the Reaper abstract class
 */
class ReaperTest extends BaseActorTest {

  val stub = TestProbe()

  case class AllSoulsReaped()

  class TestReaper extends Reaper {
    override def allSoulsReaped(): Unit = stub.ref ! AllSoulsReaped()
  }

  it should "trigger allSoulsReaped() when all watched Actors terminate" in {
    val testReaper = testActor(Props(new TestReaper))
    val instance = testReaper.underlyingActor.asInstanceOf[TestReaper]

    // Create a couple test Actors
    val a = TestProbe()
    val b = TestProbe()

    testReaper ! Watch(a.ref)
    instance.watched should contain(a.ref)

    testReaper ! Watch(b.ref)
    instance.watched should contain(b.ref)

    // Terminate all watched actors
    system.stop(a.ref)
    system.stop(b.ref)

    stub.expectMsg(AllSoulsReaped())
  }

}
