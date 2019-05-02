package edge.labs.orchestrator.pipelines

import edge.labs.orchestrator.dag.Graphable
import edge.labs.orchestrator.jobs.Job

/* @author medge */

/**
 * Representation of a collection of related Jobs
 */
case class Pipeline(
  runDate: String = "", // Filled in by the PipelineRepository
  id: String,
  version: String,
  dependencies: Set[String],

  continueOnFailure: Boolean = false,

  baseUrl: String = "",

  jobs: Array[Job]
) extends Graphable[Pipeline, String] {

  /**
   * Create a new Pipeline model with the runDate field and every Job object's runDate field with the given runDate
   *
   * @param runDate String
   * @return Pipeline
   */
  def withRunDate(runDate: String): Pipeline = {
    val jobsWithRunDate = jobs.map(_.copy(runDate = runDate))

    this.copy(runDate = runDate, jobs = jobsWithRunDate)
  }

  /**
   * Return a new Pipeline model with it's baseUrl propogated to each Job model it contains
   * @return
   */
  def withPropagatedBaseUrl(): Pipeline = {
    withBaseUrl(this.baseUrl)
  }

  /**
   * Create a new Pipeline model with the baseUrl field and every Job object's baseUrl field with the given baseUrl
   * that doesn't already have a baseUrl defined.
   *
   * i.e if a Job has baseUrl defined, leave it alone
   *
   * @param baseUrl String
   * @return Pipeline
   */
  def withBaseUrl(baseUrl: String): Pipeline = {

    // Copy baseUrl given to Jobs that do not have a baseUrl defined
    val jobsWithoutBaseUrl =
      jobs
        .filter(_.baseUrl.isEmpty)
        .map(_.copy(baseUrl = baseUrl))

    // Grab Jobs that already have a baseUrl populated
    val jobsWithBaseUrl = jobs.filter(_.baseUrl.nonEmpty)

    // Return the two Job collections merged together
    this.copy(jobs = jobsWithoutBaseUrl ++ jobsWithBaseUrl)
  }

  override def toString: String = {
    s"Pipeline($runDate, $id, $version, dependencies=[${dependencies.mkString(", ")}], $baseUrl, jobs=[${jobs.map(_.toString).mkString(", ")}])"
  }
}
