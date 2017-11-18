package edge.labs.orchestrator.dag

import edge.labs.orchestrator.jobs.Job
import edge.labs.orchestrator.pipelines.Pipeline
import edge.labs.orchestrator.{BaseTest, TestModels}

/* @author medge */

/**
 * Unit tests to ensure a DAG of objects (Pipelines, Jobs, etc) can be properly
 * created.
 *
 * Mainly a test of the DAG.from() method
 */
class RunnerDagTest extends BaseTest with TestModels {

  "DAG.from()" should "produce the correct DAG for a Pipeline model" in {
    val result = DAG.from[String, Pipeline](Set(testPipelineA, testPipelineB, testPipelineC, testPipelineD))

    // Use testDAG to test equality
    result.getNodes.diff(testPipelineDAG.getNodes) shouldBe empty
    result.getDependencies.diff(testPipelineDAG.getDependencies) shouldBe empty
    result.startNodes().diff(testPipelineDAG.startNodes()) shouldBe empty
    result.exitNodes().diff(testPipelineDAG.exitNodes()) shouldBe empty

    // For extra assurance - the predecessors/successors should also match
    result.predecessors(testPipelineA) shouldEqual testPipelineDAG.predecessors(testPipelineA)
    result.successors(testPipelineA) shouldEqual testPipelineDAG.successors(testPipelineA)

    result.predecessors(testPipelineB) shouldEqual testPipelineDAG.predecessors(testPipelineB)
    result.successors(testPipelineB) shouldEqual testPipelineDAG.successors(testPipelineB)

    result.predecessors(testPipelineC) shouldEqual testPipelineDAG.predecessors(testPipelineC)
    result.successors(testPipelineC) shouldEqual testPipelineDAG.successors(testPipelineC)

    result.predecessors(testPipelineD) shouldEqual testPipelineDAG.predecessors(testPipelineD)
    result.successors(testPipelineD) shouldEqual testPipelineDAG.successors(testPipelineD)
  }

  // fromSingle is just a small abstraction of from() so this test only should suffice
  "DAG.fromSingle()" should "produce the correct DAG for a Pipeline model" in {
    val result = DAG.fromSingle[String, Pipeline](testPipelineA)

    // Use testDAG to test equality
    result.getNodes should contain only testPipelineA

    result.predecessors(testPipelineA) shouldBe empty
    result.successors(testPipelineA) shouldBe empty
  }

  "DAG.from()" should "return an empty DAG if the collection of Pipeline models is empty" in {
    val result = DAG.from[String, Pipeline](Set.empty)

    result.getNodes shouldBe empty
    result.getDependencies shouldBe empty
    result.startNodes() shouldBe empty
    result.exitNodes() shouldBe empty
  }

  "DAG.from()" should "produce the correct DAG for a Job model" in {
    val result = DAG.from[String, Job](Set(testJobA, testJobB, testJobC, testJobD))

    // Use testDAG to test equality
    result.getNodes.diff(testJobDAG.getNodes) shouldBe empty
    result.getDependencies.diff(testJobDAG.getDependencies) shouldBe empty
    result.startNodes().diff(testJobDAG.startNodes()) shouldBe empty
    result.exitNodes().diff(testJobDAG.exitNodes()) shouldBe empty

    // For extra assurance - the predecessors/successors should also match
    result.predecessors(testJobA) shouldEqual testJobDAG.predecessors(testJobA)
    result.successors(testJobA) shouldEqual testJobDAG.successors(testJobA)

    result.predecessors(testJobB) shouldEqual testJobDAG.predecessors(testJobB)
    result.successors(testJobB) shouldEqual testJobDAG.successors(testJobB)

    result.predecessors(testJobC) shouldEqual testJobDAG.predecessors(testJobC)
    result.successors(testJobC) shouldEqual testJobDAG.successors(testJobC)

    result.predecessors(testJobD) shouldEqual testJobDAG.predecessors(testJobD)
    result.successors(testJobD) shouldEqual testJobDAG.successors(testJobD)
  }

  "DAG.from()" should "return an empty DAG if the collection of Job models is empty" in {
    val result = DAG.from[String, Job](Set.empty)

    result.getNodes shouldBe empty
    result.getDependencies shouldBe empty
    result.startNodes() shouldBe empty
    result.exitNodes() shouldBe empty
  }

}
