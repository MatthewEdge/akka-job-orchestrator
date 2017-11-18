package edge.labs.orchestrator.pipelines

import edge.labs.orchestrator.{BaseTest, TestModels}

/* @author medge */

/**
 * Unit Tests for the logic methods found in the Pipeline model class
 */
class PipelineTest extends BaseTest with TestModels {

  "withRunDate()" should "copy runDate to all Job models" in {
    val pipeline = testPipelineA.copy(jobs = Array(testJobA, testJobB))

    val testRunDate = "14000101"

    val resultJobs = pipeline.withRunDate(testRunDate).jobs

    // All jobs should now have runDate populated with the given testRunDate
    resultJobs should contain only(
      testJobA.copy(runDate = testRunDate),
      testJobB.copy(runDate = testRunDate)
    )
  }

  "withPropagatedBaseUrl()" should "propagate baseUrl to all Job models that do NOT have baseUrl defined explicitly" in {
    val jobA = testJobA.copy(baseUrl = "OVERRIDE")
    val jobB = testJobB

    val pipeline = testPipelineA.copy(jobs = Array(jobA, jobB))

    val resultJobs = pipeline.withPropagatedBaseUrl().jobs

    resultJobs should contain only(
      jobA, // jobA should have kept its OVERRIDE baseUrl
      jobB.copy(baseUrl = testPipelineA.baseUrl) // jobB should now have testPipelineA's baseUrl populated
    )
  }

}
