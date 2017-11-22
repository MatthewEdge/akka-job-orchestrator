package edge.labs.orchestrator

import akka.actor.{ActorContext, ActorRef, Props}
import edge.labs.orchestrator.actor.BaseActor
import edge.labs.orchestrator.dag.DAG
import edge.labs.orchestrator.pipelines._


object Supervisor {

  /**
   * Props factory which canonicalizes how a Supervisor Actor instance is created
   *
   * @param actorCreator Function that abstracts how child Actors are actually created
   * @param dag DAG[Pipeline] to execute
   * @return Props
   */
  def props(actorCreator: (ActorContext, Pipeline) => ActorRef, dag: DAG[Pipeline], runDate: String) = {
    Props(
      new Supervisor(actorCreator, dag, runDate)
    )
  }

  /**
   * Kick off a run
   */
  case class Start() extends Command

  /**
   * Run completed successfully
   *
   * @param runDate String
   */
  case class Completed(runDate: String) extends Event

  /**
   * Run failed to complete
   *
   * @param runDate String
   * @param reason String
   * @param cause Throwable (default null)
   */
  case class Failed(runDate: String, reason: String, cause: Throwable = null)
    extends Event

}

/**
 * Supervisor Actor responsible for orchestrating the end-to-end execution of
 * a DAG of Pipelines. Delegates to a PipelineSupervisor to execute the Jobs in order
 *
 * @author medge
 */
class Supervisor(
  actorCreator: (ActorContext, Pipeline) => ActorRef,
  dag: DAG[Pipeline],
  runDate: String
) extends BaseActor {

  import Supervisor._

  var submitted: Set[Pipeline] = Set.empty
  var completed: Set[Pipeline] = Set.empty
  var failed: Set[Pipeline] = Set.empty

  /**
   * Create a child PipelineSupervisorActor
   *
   * @return ActorRef
   */
  def createPipelineSupervisorFor(pipeline: Pipeline): ActorRef = {
    actorCreator(context, pipeline)
  }

  def receive = {

    // Pipeline DAG retrieved, start executing the DAG returned
    case Start() =>
      dag.startNodes().foreach(startPipeline)

    // ACK that the PipelineSupervisor started successfully
    case PipelineStarted(pRunDate, pipeline) =>
      markSubmitted(pipeline)

    case PipelineCompleted(pRunDate, pipeline) =>
      markCompleted(pipeline)
      next(pipeline)

    case PipelineFailed(pRunDate, pipeline, reason, cause) =>
      markFailed(pipeline, reason, cause)

      if(pipeline.continueOnFailure)
        next(pipeline)
      else
        finishAndShutdown()

    case m @ _ =>
      log.warning(s"Unrecognized message sent to Supervisor: $m")
      unhandled(m)
  }

  /**
   * Submit the given Pipeline for execution by a child PipelineSupervisor
   *
   * @param pipeline Pipeline
   */
  def startPipeline(pipeline: Pipeline): Unit = {
    val pipelineSupervisor = createPipelineSupervisorFor(pipeline)

    log.info(s"Starting Pipeline ${pipeline.id}")

    pipelineSupervisor ! StartPipeline()
  }

  /**
   * Check if DAG is complete. If so, finish and shutdown. If not, kick off the next
   * Job in the DAG
   *
   * @param lastCompleted Job
   */
  def next(lastCompleted: Pipeline) = {

    // Check both completed AND failed for a full DAG view
    val dagComplete = dag.exitNodes().diff(completed ++ failed).isEmpty

    if(dagComplete) {
      finishAndShutdown()
    } else {
      startNextPipeline(lastCompleted)
    }
  }

  /**
   * Query the DAG for the next set of Pipelines to execute and submit each (if any)
   * for execution
   *
   * @param lastCompleted Pipeline that just completed
   */
  def startNextPipeline(lastCompleted: Pipeline): Unit = {
    val nextPipelines = dag.successors(lastCompleted)

    // Filter out Pipelines which have already been submitted/completed
    val potential = nextPipelines.diff(submitted).diff(completed ++ failed)

    // If a Pipeline's dependencies are completed, only then submit it
    potential
      .filter(pipeline => dag.predecessors(pipeline).forall(dep => completed.contains(dep) || failed.contains(dep))) // check that dependencies are completed
      .foreach(startPipeline)
  }

  /**
   * Executed once all Pipelines complete (success or failure). Generates the appropriate
   * response based on success/failures
   */
  def finishAndShutdown(): Unit = {

    // If failures, notify of the failures. Otherwise signal a PipelineCompleted
    if(failed.nonEmpty) {
      publish(Failed(runDate, s"There were Pipeline failures: [${failed.mkString(", ")}]"))
    } else {
      publish(Completed(runDate))
    }

    stop()

  }

  /**
   * Mark a Pipeline as started and publish a PipelineStarted event
   *
   * @param pipeline Pipeline
   */
  def markSubmitted(pipeline: Pipeline) = {
    submitted += pipeline

    publish(PipelineStarted(pipeline))
  }

  /**
   * Mark given Pipeline as Completed
   *
   * @param pipeline Pipeline
   */
  def markCompleted(pipeline: Pipeline) = {
    completed += pipeline

    publish(PipelineCompleted(pipeline))
  }

  /**
   * Mark given Pipeline as Failed
   *
   * @param pipeline Pipeline
   */
  def markFailed(pipeline: Pipeline, reason: String, cause: Throwable = null) = {
    failed += pipeline

    publish(PipelineFailed(pipeline, reason, cause))
  }
}