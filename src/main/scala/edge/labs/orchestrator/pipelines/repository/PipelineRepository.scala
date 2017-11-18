package edge.labs.orchestrator.pipelines.repository

import java.io.File

import edge.labs.orchestrator.dag.DAG
import edge.labs.orchestrator.pipelines.Pipeline
import edge.labs.orchestrator.util.json.JsonSupport

import scala.concurrent.{ExecutionContext, Future}

/* @author medge */

/**
 * Class responsible for reading in and validating the structure of
 * a DAG of Pipelines.
 *
 * Reads in all available Pipeline JSON files from the given path and constructs
 * them into a DAG of Pipeline objects.
 */
object PipelineRepository extends JsonSupport {

  /**
   * Collect all Pipeline JSON files into a DAG[Pipeline] object from the given file path
   *
   * @param path String file path to the FOLDER that contains the JSON files (not recursive)
   * @param newRunDate String runDate to attach to the generated Pipelines
   * @throws DuplicatePipelinesException if multiple Pipeline definitions exist with the same ID
   */
  @throws[DuplicatePipelinesException]
  def fetchDAG(path: String, newRunDate: String)(implicit ctx: ExecutionContext): Future[DAG[Pipeline]] = Future {
    val jsonFiles = new File(path).listFiles().filter(isJsonFile)

    val pipelines =
      jsonFiles
        .map(readJsonFile)
        .map(jsonTo[Pipeline]) // Parse to a Pipeline object
        .map(pipeline => {
          pipeline.withBaseUrl(pipeline.baseUrl).withRunDate(newRunDate)
        }
      )

    failIfDuplicatedPipelinesExist(pipelines)

    DAG.from[String, Pipeline](pipelines)
  }

  /**
   * Finds any duplicate IDs within the given Array[Pipeline] and fails out if any exist
   *
   * @param pipelines Array[Pipeline]
   * @throws DuplicatePipelinesException if any duplicate IDs exist in the given Array
   */
  @throws[DuplicatePipelinesException]
  def failIfDuplicatedPipelinesExist(pipelines: Array[Pipeline]): Unit = {
    val ids = pipelines.map(_.id)

    // Return the difference of the given IDs to the distinct set of IDs
    val dupes = ids.diff(ids.distinct).toSet

    if(dupes.nonEmpty)
      throw new DuplicatePipelinesException(s"Duplicate Pipelines exist with the same ID! duplicates=[${dupes.mkString(",")}]")
  }

}

/**
 * Exception to mark that duplicate Pipeline IDs were read in from the given Pipeline JSON files
 */
case class DuplicatePipelinesException(msg: String, cause: Throwable = null) extends Exception(msg, cause)