package edge.labs.orchestrator.events.repository

import edge.labs.orchestrator.jobs.{JobCompleted, JobStarted}
import edge.labs.orchestrator.{BaseTest, Event, TestModels}

/* @author medge */

/**
 * Unit Tests for the In Memory variant of the EventRepository interface
 */
class PersistentInMemEventRepositoryTest extends BaseTest with TestModels {

  it should "save the given event" in {
    val repo = PersistentInMemEventRepository(
      persist = (runDate: String, evts: Set[Event]) => {} // no-op
    )

    val resultFuture =
      for {
        evt1 <- repo.saveEvent("18000101", JobStarted(testJobA))    // Seed an initial event
        evt2 <- repo.saveEvent("18000101", JobCompleted(testJobA))  // This should APPEND an event to the previous entry
        events <- repo.findByRunDate("18000101")                    // Lastly, query for the events
      }
      yield events

    val result = awaitFuture(resultFuture)

    result should contain only(JobStarted(testJobA), JobCompleted(testJobA))
  }

  it should "return an empty Set for an unknown runDate" in {
    val repo = PersistentInMemEventRepository(
      persist = (runDate: String, evts: Set[Event]) => {} // no-op
    )

    val result = awaitFuture(repo.findByRunDate("18000101"))
    result shouldBe empty
  }

}
