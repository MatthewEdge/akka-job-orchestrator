package edge.labs.orchestrator.pipelines.repository

import edge.labs.orchestrator.jobs.Job
import edge.labs.orchestrator.{BaseTest, TestModels}
import org.scalatest.concurrent.ScalaFutures

/* @author medge */

/**
 * Unit tests for the DagReader class.
 */
class PipelineRepositoryTest extends BaseTest with ScalaFutures with TestModels {

  val testReader = PipelineRepository

  val testPipelines = Array(testPipelineA, testPipelineB, testPipelineC, testPipelineD)

  "failIfDuplicatesIn()" should "throw a DuplicatePipelinesException if dupes exists" in {
    val throwable = intercept[DuplicatePipelinesException] {
      testReader.failIfDuplicatedPipelinesExist(
        Array(
          testPipelineA, testPipelineA,
          testPipelineB,
          testPipelineC
        )
      )
    }

    throwable.isInstanceOf[DuplicatePipelinesException] shouldBe true
  }

  "failIfDuplicatesIn()" should "not throw a DuplicatePipelinesException if dupes do not exist" in {
    try {
      testReader.failIfDuplicatedPipelinesExist(
        Array(testPipelineA, testPipelineB, testPipelineC)
      )
    } catch {
      case e: Throwable => fail(s"Was not supposed to throw an exception but found $e")
    }
  }

  "fetchDAG()" should "create a proper DAG from the given test JSON" in {
    val testJson = testResource("/test/resources/singleDag")
    val dagFuture = testReader.fetchDAG(testJson, testRunDate)

    whenReady(dagFuture) { dag =>
      val pipelines = dag.getNodes

      pipelines should have size 1

      // Make assertions against the given Pipeline
      val pipeline = pipelines.toList.head

      pipeline.id shouldEqual "TestPipelineA"
      pipeline.version shouldEqual "1"
      pipeline.dependencies shouldBe empty
      pipeline.baseUrl shouldEqual "http://localhost"

      // Validate the Jobs
      pipeline.jobs should have size 2

      pipeline.jobs(0) should equal (
        Job(
          runDate = "18000101", // ensure the Run Date was populated correctly
          id = "pipelineA-jobA",
          description = "Standard REST Test",
          dependencies = Set.empty,

          baseUrl = "http://localhost",
          endpoint = "/A",
          method = "GET",
          queryParams = Some(Map("foo" -> "bar")),

          pollEndpoint = None,
          pollMethod = None,
          pollQueryParams = None,
          pollFor = None,
          pollFailure = None
        )
      )

      pipeline.jobs(1) should equal (
        Job(
          runDate = "18000101", // ensure the Run Date was populated correctly
          id = "pipelineA-jobB",
          description = "Polling REST Test",
          dependencies = Set.empty,

          baseUrl = "http://localhost",
          endpoint = "/B",
          method = "GET",
          queryParams = None,

          pollEndpoint = Some("/pollB"),
          pollMethod = Some("GET"),
          pollQueryParams = None,
          pollFor = Some(Map(
            "status" -> "Completed"
          )),
          pollFailure = Some(Map(
            "status" -> "Failed"
          ))
        )
      )
    }
  }
}
