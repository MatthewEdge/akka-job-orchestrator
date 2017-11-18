package edge.labs.orchestrator.events.repository

import akka.event.slf4j.Logger
import edge.labs.orchestrator.{Event, Settings}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

/* @author medge */

/**
 * In Memory implementation of the EventRepository backed by a simple Map[ String, Set[Event] ]. Accepts a
 * persist(runDate, Set[Event]) function which will persist the given events for the given runDate in some manner.
 */
case class PersistentInMemEventRepository(
  persist: (String, Set[Event]) => Unit
) extends EventRepository {

  val log = Logger("PersistentInMemEventRepository")

  val settings = Settings()
  val daysToKeep = settings.daysToKeep

  private[this] var events: Map[String, Set[Event]] = Map.empty

  /**
   * Save the given event for the given runDate
   *
   * @param runDate String
   * @param event Event
   * @return Future[Boolean] success flag
   */
  override def saveEvent(runDate: String, event: Event)(implicit ec: ExecutionContext): Future[Boolean] = Future {
    val existing = events.getOrElse(runDate, Set.empty)
    val withNewEvt = existing + event

    events = events + (runDate -> withNewEvt)

    true
  }

  /**
   * Find all Events for the given Run Date
   *
   * @param runDate String
   * @return Future[ Set[Event] ] - Set.empty if runDate does not have any events stored
   */
  override def findByRunDate(runDate: String)(implicit ec: ExecutionContext): Future[Set[Event]] = Future {
    events.getOrElse(runDate, Set.empty)
  }

  /**
   * Persist events for the given runDate using the provided persist() lambda
   *
   * @param runDate String runDate to persist events for
   * @param ec implicit ExecutionContext
   * @return Future[Boolean] success flag
   */
  override def persistEvents(runDate: String)(implicit ec: ExecutionContext): Future[Boolean] = {

    log.info(s"Persisting events for runDate $runDate")

    findByRunDate(runDate)
      .map(evts => {
        persist(runDate, evts)

        true
      })
      .recover {
        case ex: Throwable =>
          log.error(s"Failed to persist events for runDate $runDate", ex)

          false
      }
      // Finally, as a last step, execute flushOldEvents()
      .andThen {
        case Success(result) =>
          discardOldEvents()

          result
      }
  }

  /**
   * Flush events for runDates older than the Settings-configured range
   *
   * @return Future[Unit] to process async
   */
  def discardOldEvents()(implicit ec: ExecutionContext): Future[Unit] = Future {
    val runDates = events.keys.toList.sorted.reverse // descending list of runDates

    // Whatever remains in this list after drop() is a runDate to remove from the Map
    val dropDates = runDates.drop(daysToKeep)

    log.info(s"Flushing ${dropDates.size} old events: [${dropDates.mkString(", ")}]")

    events = events.filter {
      case (key, value) => !dropDates.contains(key) // So filter the Map to only keys not in dropDates
    }
  }
}
