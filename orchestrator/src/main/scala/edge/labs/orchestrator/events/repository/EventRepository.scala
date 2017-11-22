package edge.labs.orchestrator.events.repository

import edge.labs.orchestrator.Event

import scala.concurrent.{ExecutionContext, Future}

/* @author medge */

/**
 * Store for Event data tied to a runDate.
 *
 * All methods return Futures to conform to the Actor concurrency expectations and thus
 * an implicit ExecutionContext must be in scope
 */
trait EventRepository {

  /**
   * Find all Events for the given Run Date
   *
   * @param runDate String
   * @param ec implicit ExecutionContext
   * @return Future[ Set[Event] ]
   */
  def findByRunDate(runDate: String)(implicit ec: ExecutionContext): Future[Set[Event]]

  /**
   * Save the given event for the given runDate
   *
   * @param runDate String
   * @param event Event
   * @param ec implicit ExecutionContext
   * @return Future[Boolean] success flag
   */
  def saveEvent(runDate: String, event: Event)(implicit ec: ExecutionContext): Future[Boolean]

  /**
   * Persist events for the given runDate to a permanent store
   *
   * @param runDate String runDate to persist events for
   * @param ec implicit ExecutionContext
   * @return Future[Boolean] success flag
   */
  def persistEvents(runDate: String)(implicit ec: ExecutionContext): Future[Boolean]

}
