package edge.labs.orchestrator.ordering

import java.time.LocalDateTime

/* @author medge */

/**
 * Scala Sorting orderings that cover types not covered in Scala by default
 */
object Orderings {

  // LocalDateTime ASCENDING ordering
  implicit def LocalDateTimeAsc: Ordering[LocalDateTime] = Ordering.fromLessThan(_ isBefore _)

  // LocalDateTime DESCENDING ordering
  implicit def LocalDateTimeDesc: Ordering[LocalDateTime] = Ordering.fromLessThan(_ isAfter _)
}
