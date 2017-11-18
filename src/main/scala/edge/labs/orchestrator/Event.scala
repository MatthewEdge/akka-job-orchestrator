package edge.labs.orchestrator

import java.time.LocalDateTime

/* Events created by an Actor that are published on the Event Stream */
abstract class Event {
  val runDate: String
  val eventTs: LocalDateTime = LocalDateTime.now()
}