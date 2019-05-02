package edge.labs.orchestrator

import edge.labs.orchestrator.Supervisor.{Completed, Failed}
import edge.labs.orchestrator.jobs.{JobEvent, JobFailed}
import edge.labs.orchestrator.pipelines.{PipelineEvent, PipelineFailed}

import edge.labs.orchestrator.ordering.Orderings.LocalDateTimeDesc

/* @author medge */

/**
 * Model to represent a Status message. Mostly used by the REST API
 */
case class Status(
  runDate: String,
  status: String,
  pipelineEvents: Array[Map[String, Any]],
  jobEvents: Array[Map[String, Any]]
)

object Status {

  def apply(runDate: String, status: String): Status = {
    new Status(runDate, status, Array.empty, Array.empty)
  }

  /**
   * Creates a new Status instance with the status field derived from the given Events array
   *
   * @param runDate String
   * @param events Array[Event]
   * @return Status
   */
  def apply[T <: Event](runDate: String, events: Array[T]): Status = {
    val derivedStatus =
      if(events.isEmpty)
        StatusEnum.NOT_RUNNING
      else if(events.exists(_.isInstanceOf[Completed]))
        StatusEnum.COMPLETED
      else if(events.exists(_.isInstanceOf[Failed]))
        StatusEnum.FAILED
      else if(events.exists(_.isInstanceOf[PipelineFailed]))
        StatusEnum.FAILED
      else if(events.exists(_.isInstanceOf[JobFailed]))
        StatusEnum.FAILED
      else
        StatusEnum.RUNNING

    // Now separate the events by type and map them to their REST representation
    // Sorts them DESC to have the most recent events first

    val jobEvents =
      events
        .sortBy(_.eventTs)(LocalDateTimeDesc)
        .filter(_.isInstanceOf[JobEvent])
        .map(eventToMap)

    val pipelineEvents =
      events
        .sortBy(_.eventTs)(LocalDateTimeDesc)
        .filter(_.isInstanceOf[PipelineEvent])
        .map(eventToMap)

    new Status(runDate, derivedStatus, pipelineEvents, jobEvents)
  }

  /**
   * Convert an Event model to a JSON-parseable Map[String, String]
   *
   * @param evt Event
   * @return Map[String, String]
   */
  def eventToMap(evt: Event): Map[String, Any] = {
    Map(
      "status" -> evt.getClass.getSimpleName,
      "eventTs" -> evt.eventTs.toString,
      "event" -> evt
    )
  }

}

object StatusEnum {
  val NOT_RUNNING = "Not Running"
  val RUNNING = "Running"
  val COMPLETED = "Completed"
  val FAILED = "Failed"
}
