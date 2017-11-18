package edge.labs.orchestrator

import edge.labs.orchestrator.dag.{DAG, Graphable}
import org.scalatest.Matchers

/* @author medge */

/**
 * Helpers for working with DAG classes
 */
trait DagHelpers extends Matchers {

  def assertDagsMatch[I, T <: Graphable[T, I]](dagA: DAG[T], dagB: DAG[T]) = {
    dagA.getNodes.diff(dagB.getNodes) shouldBe empty
    dagA.getDependencies.diff(dagB.getDependencies) shouldBe empty
    dagA.startNodes().diff(dagB.startNodes()) shouldBe empty
    dagA.exitNodes().diff(dagB.exitNodes()) shouldBe empty
  }

}
