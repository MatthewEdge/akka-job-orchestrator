package edge.labs.orchestrator.events

import akka.actor.Props
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
   */
  case class FetchEvents(runDate: String) extends Command

  /**
   * Command containing the requested Events
   */
  case class ReceiveEvents(runDate: String, events: Set[Event]) extends Command

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
        .map { events =>
          log.debug(s"Returning ${events.size} events back for $runDate")
          ReceiveEvents(runDate, events)
        }
        .recover {
          case cause: Throwable =>
            log.error("Failed to fetch events from the EventRepository", cause)
            ReceiveEvents(runDate, Set.empty)
        }
        .pipeTo(origSender)

    case m @ _ =>
      log.warning(s"Unrecognized message sent to EventReader: $m")
      unhandled(m)
  }

}
