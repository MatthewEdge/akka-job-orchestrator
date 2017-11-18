package edge.labs.orchestrator.events

import akka.testkit.TestProbe
import edge.labs.orchestrator.events.EventReader.{FetchEvents, ReceiveEvents}
import edge.labs.orchestrator.events.repository.{EventRepository, PersistentInMemEventRepository}
import edge.labs.orchestrator.jobs.JobStarted
import edge.labs.orchestrator.{BaseActorTest, Event, TestModels}

/* @author medge */

/**
 * Unit Tests for the EventReader Actor
 */
class EventReaderTest extends BaseActorTest with TestModels {

  val testSender = TestProbe()

  def actorUnderTest(repo: EventRepository) = testActor(EventReader.props(repo))

  it should "return all available events for the given runDate" in {
    val testRepo = PersistentInMemEventRepository(
      persist = (runDate: String, evts: Set[Event]) => {} // no-op
    )

    val actor = actorUnderTest(testRepo)

    val testRunDate = "14000101"

    // Seed known events into EventRepository
    val evtA = JobStarted(testRunDate, testJobA)
    val evtB = JobStarted(testRunDate, testJobB)

    for {
      seedA <- testRepo.saveEvent(testRunDate, evtA)
      seedB <- testRepo.saveEvent(testRunDate, evtB)
    }
      yield {}

    // Have testSender send the EventReader the Fetch request
    testSender.send(actor, FetchEvents(testRunDate))

    testSender.expectMsg(
      ReceiveEvents(
        testRunDate,
        Set(evtA, evtB)
      )
    )
  }

  it should "return an empty Set for an unknown runDate" in {
    val testRepo = PersistentInMemEventRepository(
      persist = (runDate: String, evts: Set[Event]) => {} // no-op
    )

    val actor = actorUnderTest(testRepo)

    val testRunDate = "99999999"

    // Have testSender send the EventReader the Fetch request
    testSender.send(actor, FetchEvents(testRunDate))

    testSender.expectMsg(
      ReceiveEvents(
        testRunDate,
        Set.empty
      )
    )
  }

  it should "not handle an unknown message" in {
    val actor = actorUnderTest(null)

    expectUnhandled("WHAT'S UP DOC?", actor)
  }

}
