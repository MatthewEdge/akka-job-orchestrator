package edge.labs.orchestrator

import edge.labs.orchestrator.dag.DAG
import edge.labs.orchestrator.jobs.Job
import edge.labs.orchestrator.pipelines.Pipeline

/* @author medge */

/**
 * Test models for use in the various Tests. Centralized to
 * ease updates needed if the base model changes
 */
trait TestModels {

  val testBaseUrl = "http://localhost"
  val testRunDate = "18000101"

  val continueOnFailure = false

  // Test Pipeline models
  val testPipelineA = Pipeline(
    runDate = testRunDate,
    id = "A",
    version = "1",
    dependencies = Set.empty,
    continueOnFailure = false,
    baseUrl = testBaseUrl,
    jobs = Array.empty
  )

  val testPipelineB = Pipeline(
    runDate = testRunDate,
    id = "B",
    version = "1",
    dependencies = Set("A"),
    continueOnFailure = false,
    baseUrl = testBaseUrl,
    jobs = Array.empty
  )

  val testPipelineC = Pipeline(
    runDate = testRunDate,
    id = "C",
    version = "1",
    dependencies = Set("A"),
    continueOnFailure = false,
    baseUrl = testBaseUrl,
    jobs = Array.empty
  )

  val testPipelineD = Pipeline(
    runDate = testRunDate,
    id = "D",
    version = "1",
    dependencies = Set("B", "C"),
    continueOnFailure = false,
    baseUrl = testBaseUrl,
    jobs = Array.empty
  )

  /*
   * A simple test DAG of Pipelines of the form:
   *
   *        A
   *       / \
   *      B   C
   *       \ /
   *        D
   */
  val testPipelineDAG = new DAG[Pipeline](
    Set(testPipelineA, testPipelineB, testPipelineC, testPipelineD),
    Set(
      (testPipelineA, testPipelineB),
      (testPipelineA, testPipelineC),
      (testPipelineB, testPipelineD),
      (testPipelineC, testPipelineD)
    )
  )

  val testJobA = Job(
    runDate = testRunDate,
    id = "A",
    description = "Test Job A",
    dependencies = Set.empty,

    baseUrl = "http://localhost",
    endpoint = "/A",
    method = "GET",
    queryParams = None,

    pollEndpoint = None,
    pollMethod = None,
    pollQueryParams = None,
    pollFor = None,
    pollFailure = None
  )

  val testJobB = Job(
    runDate = testRunDate,
    id = "B",
    description = "Test Job B",
    dependencies = Set("A"),

    baseUrl = "http://localhost",
    endpoint = "/B",
    method = "GET",
    queryParams = None,

    pollEndpoint = None,
    pollMethod = None,
    pollQueryParams = None,
    pollFor = None,
    pollFailure = None
  )

  val testJobC = Job(
    runDate = testRunDate,
    id = "C",
    description = "Test Job C",
    dependencies = Set("A"),

    baseUrl = "http://localhost",
    endpoint = "/C",
    method = "GET",
    queryParams = None,

    pollEndpoint = None,
    pollMethod = None,
    pollQueryParams = None,
    pollFor = None,
    pollFailure = None
  )

  val testJobD = Job(
    runDate = testRunDate,
    id = "D",
    description = "Test Job D",
    dependencies = Set("B", "C"),

    baseUrl = "http://localhost",
    endpoint = "/D",
    method = "GET",
    queryParams = None,

    pollEndpoint = None,
    pollMethod = None,
    pollQueryParams = None,
    pollFor = None,
    pollFailure = None
  )

  /*
   * A simple test DAG of Jobs of the form:
   *
   *        A
   *       / \
   *      B   C
   *       \ /
   *        D
   */
  val testJobDAG = new DAG[Job](
    Set(testJobA, testJobB, testJobC, testJobD),
    Set(
      (testJobA, testJobB),
      (testJobA, testJobC),
      (testJobB, testJobD),
      (testJobC, testJobD)
    )
  )
  
}
