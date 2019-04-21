package edge.labs.orchestrator.events

import akka.actor.Props
import edge.labs.orchestrator.events.repository.{EventRepository, PersistentInMemEventRepository}
import edge.labs.orchestrator.jobs.JobStarted
import edge.labs.orchestrator.{BaseActorTest, Event, TestModels}

import scala.concurrent.Await
import scala.concurrent.duration._

/* @author medge */

/**
 * Unit Tests for the EventWriter Actor base implementation
 */
class EventWriterTest extends BaseActorTest with TestModels {

  /**
   * TestEventWriter implementation that simply uses the EventRepository#saveEvent method
   * for logEvent and logRunCompleted. i.e just delegate to the eventRepository
   *
   * @param eventRepository EventRepository
   */
  class TestEventWriter(eventRepository: EventRepository) extends EventWriter(eventRepository) {
    override def logEvent(evt: Event): Unit = Await.result(eventRepository.saveEvent(evt.runDate, evt), 5.seconds)
    override def logRunCompleted(evt: Event): Unit = Await.result(eventRepository.saveEvent(evt.runDate, evt), 5.seconds)
  }

  def actorUnderTest(repo: EventRepository) = testActor(Props(new TestEventWriter(repo)))

  it should "persist a given Event" in {
    val testRepo = PersistentInMemEventRepository(
      persist = (runDate: String, evts: Set[Event]) => {} // no-op
    )

    val actor = actorUnderTest(testRepo)

    // Send a Event to the Actor. It should persist the event to the EventRepository
    val evt = JobStarted("14000101", testJobA)

    actor ! evt

    val results = awaitFuture(testRepo.findByRunDate("14000101"))

    results should contain only evt

  }

  it should "append a given Event to an existing collection" in {
    val testRepo = PersistentInMemEventRepository(
      persist = (runDate: String, evts: Set[Event]) => {} // no-op
    )

    val actor = actorUnderTest(testRepo)

    val evtA = JobStarted("14000101", testJobA)
    val evtB = JobStarted("14000101", testJobB)

    actor ! evtA
    actor ! evtB

    val results = awaitFuture(testRepo.findByRunDate("14000101"))

    results should contain only(evtA, evtB)

  }

  it should "not handle an unknown message" in {
    val actor = actorUnderTest(null)

    expectUnhandled("WHAT'S UP DOC?", actor)
  }

}
