package edge.labs.orchestrator.events

import akka.event.{DiagnosticLoggingAdapter, Logging}
import edge.labs.orchestrator.Event
import edge.labs.orchestrator.Supervisor.{Completed, Failed}
import edge.labs.orchestrator.actor.BaseActor
import edge.labs.orchestrator.events.repository.EventRepository
import edge.labs.orchestrator.json.JsonSupport

/* @author medge */

object EventWriter {

  /****************************************************
   *          Internal Messages
   ****************************************************/
  sealed case class EventWriteSuccess(evt: Event)
  sealed case class EventWriteFailure(evt: Event)

}

/**
 * Base implementation of the EventWriter Actor. Defines three items that implementing classes
 * must provide:
 *
 *   EventRepository being used (constructor param)
 *   Logic for logging a single Event
 *   Logic for logging that the run completed
 *
 *  Takes care of all other logic such as message handling and Dispatcher implementation
 */
abstract class EventWriter(eventRepository: EventRepository) extends BaseActor {

  import EventWriter._

  // Dispatcher for Future execution
  implicit val eventDispatcher = getDispatcher(settings.eventActorDispatcher)

  def logEvent(evt: Event): Unit = {
    eventRepository.saveEvent(evt.runDate, evt)
  }

  def logRunCompleted(evt: Event): Unit = {
    // Save the completion event first
    logEvent(evt)

    // Then persist
    eventRepository.persistEvents(evt.runDate)
  }

  override def preStart() = {
    subscribeTo[Event]
  }

  final def receive = {
    case e: Event => e match {

      // Run Completed
      case completedEvt if e.isInstanceOf[Completed] || e.isInstanceOf[Failed] =>
        logRunCompleted(e)

      // All other events are MDC logged
      case evt @ _ =>
        logEvent(evt)
    }

    case EventWriteSuccess(evt) =>
      log.debug(s"Event write successful for $evt")

    case EventWriteFailure(evt) =>
      log.error(s"Failed to persist event to EventRepository: $evt")

    case m @ _ =>
      log.warning(s"Unrecognized message sent to EventWriter: $m")
      unhandled(m)
  }

}

/**
 * Persists Event messages as log messages to an MDC-defined logFile in addition to the default behavior of the
 * EventWriter abstract class
 */
class LogFileEventWriter(eventRepository: EventRepository) extends EventWriter(eventRepository) with JsonSupport {

  // Logging will be done using a DiagnosticLoggingAdapter to gain access to the MDC feature
  override val log: DiagnosticLoggingAdapter = Logging(this)

  final def eventLogFileName(evt: Event) = s"EventWriter-${evt.runDate}"

  /**
   * MDC log the given Event
   *
   * @param evt Event
   */
  override def logEvent(evt: Event): Unit = {
    super.logEvent(evt)

    // Then, MDC log the event
    log.mdc(Map("logFile" -> eventLogFileName(evt))) // MDC the runDate to log to the correct file

    log.info(s"$evt")

    log.clearMDC() // Clear MDC when done
  }

}
