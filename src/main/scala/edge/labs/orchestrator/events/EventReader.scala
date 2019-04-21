package edge.labs.orchestrator.events

import akka.actor.{ActorRef, Props}
import akka.pattern.pipe
import edge.labs.orchestrator.actor.BaseActor
import edge.labs.orchestrator.events.repository.EventRepository
import edge.labs.orchestrator.{Command, Event}

import scala.concurrent.ExecutionContext

/* @author medge */

object EventReader {

  def props(eventRepository: EventRepository) = Props(new EventReader(eventRepository))

  /**
   * Command to fetch all events for the given runDate
   *
   * @param runDate String
   */
  case class FetchEvents(runDate: String) extends Command

  /**
   * Command containing the requested Events
   *
   * @param runDate String
   * @param events Set[Event]
   */
  case class ReceiveEvents(runDate: String, events: Set[Event]) extends Command


  /****************************************************
   *          Internal Messages
   ****************************************************/
  sealed case class RepoFetchSuccess(origSender: ActorRef, runDate: String, events: Set[Event])
  sealed case class RepoFetchFailed(origSender: ActorRef, runDate: String, reason: Throwable)

}

/**
 * Event Reader Actor which queries the Event Repository for Events associated to runDates
 *
 * @param eventRepository EventRepository to query
 */
class EventReader(eventRepository: EventRepository) extends BaseActor {

  import EventReader._

  // Dispatcher for Future execution
  implicit val eventDispatcher: ExecutionContext = getDispatcher(settings.eventActorDispatcher)

  def receive: Receive = {
    case FetchEvents(runDate) =>
      val origSender = sender()

      eventRepository.findByRunDate(runDate)
        .map(evts => RepoFetchSuccess(origSender, runDate, evts))
        .recover {
          case cause: Throwable => RepoFetchFailed(origSender, runDate, cause)
        }
        .pipeTo(self)

    case RepoFetchSuccess(origSender, runDate, events) =>
      log.debug(s"Returning ${events.size} events back for $runDate")
      origSender ! ReceiveEvents(runDate, events)

    case RepoFetchFailed(origSender, runDate, reason) =>
      log.error("Failed to fetch events from the EventRepository", reason)
      origSender ! ReceiveEvents(runDate, Set.empty)

    case m @ _ =>
      log.warning(s"Unrecognized message sent to EventReader: $m")
      unhandled(m)
  }

}
