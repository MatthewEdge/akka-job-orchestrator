package edge.labs.orchestrator.ordering

import java.time.LocalDateTime

/* @author medge */

/**
 * Scala Sorting orderings that cover types not covered in Scala by default
 *
 * Note - orderings are ASCENDING by default to follow the Scala convention
 */
object Orderings {

  /**
   * LocalDateTime ASCENDING ordering
   */
  implicit def LocalDateTimeOrdering: Ordering[LocalDateTime] = Ordering.fromLessThan(_ isBefore _)

}
