package edge.labs.orchestrator.dag

/* @author medge */

/**
 * Directed Acyclic Graph structure....simplified
 *
 * TODO cycle detection
 *
 * @param nodes Set[T] all nodes in the DAG
 * @param dependencies Set[(T, T)] Set of nodes organized in a (from, to) relationship (mutable)
 * @tparam T Node type parameter
 */
class DAG[T](
  nodes: Set[T],
  dependencies: Set[(T, T)]
) {

  def getNodes: Set[T] = nodes
  def getDependencies: Set[(T, T)] = dependencies

  /**
   * Creates a new dependency between two nodes
   *
   * @return DAG[T] with new dependency added (hooray immutability)
   */
  def withDependency(from: T, to: T): DAG[T] = {
    val pair = (from, to)
    val newDeps = dependencies + pair

    new DAG[T](nodes, newDeps)
  }

  /**
   * Returns a Set of nodes that are `predecessors` for the given
   * node.
   *
   * A predecessor of a node is a dependency for that node. Ex:
   *  (1,2) node 1 is considered a `predecessor` for node 2
   *
   * @param node Node to query
   * @return Set[T] or empty Set if no predecessors exist
   */
  def predecessors(node: T): Set[T] = {
    dependencies
      .filter { case (from, to) => to.equals(node) }
      .map { case (from, to) => from }
  }

  /**
   * Returns a Set of nodes that are `successors` for the given
   * node.
   *
   * A successor of a node is a dependent of that node. Ex:
   * (1,2) node 2 is considered a `successor` for node 1
   *
   * @param node Node to query
   * @return Set[T] or empty Set if no successors exist
   */
  def successors(node: T): Set[T] = {
    dependencies
      .filter { case (from, to) => from.equals(node) }
      .map { case (from, to) => to }
  }

  /**
   * The start nodes of a DAG are those which have no predecessors
   *
   * @return Set[T] or empty Set. This should ONLY happen for an empty DAG
   */
  def startNodes(): Set[T] = {
    nodes.filter(node => predecessors(node).isEmpty)
  }

  /**
   * The exit nodes of a DAG are those which have no successors
   *
   * @return Set[T] or empty Set. This should ONLY happen for an empty DAG
   */
  def exitNodes(): Set[T] = nodes.filter(node => successors(node).isEmpty)

  override def toString: String = {
    s"DAG(" +
      s"nodes=${nodes.mkString(",")}, " +
      s"dependencies=${dependencies.map { case (from, to) => s"($from -> $to)" }.mkString(",")}, " +
      s"startNodes=${startNodes().mkString(",")}," +
      s"exitNodes=${exitNodes().mkString(",")}" +
    s")"
  }
}

object DAG {

  /**
   * Create a new DAG[T] from the given Graphable items
   *
   * @param nodes Traversable[T]
   * @tparam I ID type
   * @tparam T Node type
   * @return DAG[T] or empty DAG if nodes is empty
   */
  def from[I, T <: Graphable[T, I]](nodes: Traversable[T])(implicit mfI: Manifest[I], mfT: Manifest[T]): DAG[T] = {

    // short circuit for this case
    if(nodes.isEmpty)
      return new DAG[T](Set.empty, Set.empty)

    // Construct a collection of tuples of (node, nodeSuccessors) where nodeSuccessors are the current
    // node's dependencies (those which must be completed first)
    val dagPairs = nodes
      .map(node => (node, node.dependencies))
      .map {
        case (node, deps) =>
          val successors = nodes.filter(node => deps.contains(node.id))

          (node, successors)
      }

    // Transform the pairs into a proper DAG[T]
    dagPairs.foldLeft(new DAG[T](nodes.toSet, Set.empty)) {
      case (dag, (node, deps)) =>
        deps
          .map(dep => (node, dep))
          .foldLeft(dag) {
            // Added in reverse because of the (from, to) relationship
            case (acc, (to, from)) => acc.withDependency(from, to)
          }
    }
  }

  /**
   * Create a new DAG[T] containing the single node given
   *
   * @param node T node
   * @return DAG[T]
   */
  def fromSingle[I, T <: Graphable[T, I]](node: T)(implicit mfI: Manifest[I], mfT: Manifest[T]): DAG[T] = {
    val asTraversable = Array(node)

    from[I, T](asTraversable)
  }
}
