package edge.labs.orchestrator.dag

/* @author medge */

/**
 * Denotes a model which can be graphed in a DAG
 *
 * @tparam T Model type
 * @tparam I ID type parameter (how to identify the Graph node)
 */
trait Graphable[T, I] {
  val id: I
  val dependencies: Set[I]
}
