package edge.labs.orchestrator.pipelines

import akka.actor.{ActorContext, ActorRef, Props}
import edge.labs.orchestrator.actor.BaseActor
import edge.labs.orchestrator.dag.DAG
import edge.labs.orchestrator.jobs._

import scala.util.{Failure, Success, Try}

/* @author medge */

object PipelineSupervisor {

  /**
   * @param parent Parent Actor who created this instance
   * @param pipeline Pipeline to be executed
   * @param actorCreator Function that abstracts how Worker Actors are actually created based on the given Job
   * @return Props
   */
  def props(parent: ActorRef, pipeline: Pipeline, actorCreator: (ActorContext, Job) => ActorRef): Props = {
    Props(new PipelineSupervisor(parent, pipeline, actorCreator))
  }
}

/**
 * PipelineSupervisor responsible for coordinating the execution of all jobs
 * within the given Pipeline.
 *
 * Note:
 *    Pipeline-related events are sent to parent
 *    Job-related events are published to the event stream
 */
class PipelineSupervisor(
  parent: ActorRef,
  pipeline: Pipeline,
  actorCreator: (ActorContext, Job) => ActorRef
) extends BaseActor {

  val dag: DAG[Job] = DAG.from[String, Job](pipeline.jobs)
  val continueOnFailure: Boolean = pipeline.continueOnFailure

  var submitted: Set[Job] = Set.empty
  var completed: Set[Job] = Set.empty
  var failed: Set[Job] = Set.empty

  def receive: Receive = {

    case StartPipeline() =>
      val startJobs = dag.startNodes()
      parent ! PipelineStarted(pipeline)

      startJobs.foreach(startJob)

    case startedEvt: JobStarted =>
      markSubmitted(startedEvt)

    case completedEvt: JobCompleted =>
      markCompleted(completedEvt)
      next(completedEvt.job)

    case failedEvt: JobFailed =>
      markFailed(failedEvt)

      if(failedEvt.job.continueOnFailure)
        next(failedEvt.job)
      else
        finishAndShutdown()

    case m @ _ =>
      log.warning(s"Unrecognized message sent to PipelineSupervisor: $m")
      unhandled(m)
  }

  /**
   * Submit the given Job for execution by a child Worker actor
   *
   * @param job Job
   */
  def startJob(job: Job): Unit = {
    Try(actorCreator(context, job)) match {
      case Success(worker) =>
        log.info(s"Starting Job ${job.id}")
        worker ! StartJob(job)

      case Failure(ex) =>
        parent ! PipelineFailed(pipeline, s"Failed to create a Worker Actor for ${job.id}", ex)
        context.stop(self)
    }
  }

  /**
   * Check if DAG is complete. If so, finish and shutdown. If not, kick off the next
   * Job in the DAG
   *
   * @param lastCompleted Job
   */
  def next(lastCompleted: Job): Unit = {
    val dagComplete = dag.exitNodes().diff(completed ++ failed).isEmpty

    if(dagComplete) {
      finishAndShutdown()
    } else {
      startNextJob(lastCompleted)
    }
  }

  /**
   * Query the DAG for the next set of Jobs to execute and submit each (if any) for execution
   *
   * NOTE: If execution rules change to actually fail out on ANY failure, this is where you change it
   *
   * @param lastCompleted Job that just completed
   */
  def startNextJob(lastCompleted: Job): Unit = {
    val nextJobs = dag.successors(lastCompleted)

    // Filter out Jobs which have already been submitted/completed
    val potential = nextJobs.diff(submitted).diff(completed ++ failed)

    potential
      .filter(job => dag.predecessors(job).forall(dep => completed.contains(dep) || failed.contains(dep)))
      .foreach(startJob)
  }

  /**
   * Final method to execute before completion. Derives Success/Failure from the completed/failed collection of Events
   */
  def finishAndShutdown(): Unit = {
    if(failed.nonEmpty) {
      parent ! PipelineFailed(pipeline, s"There were Job failures: [${failed.mkString(", ")}]")
    } else {
      parent ! PipelineCompleted(pipeline)
    }

    // Free up resources
    context.stop(self)
  }

  /**
   * Mark a Job as started and publish a JobStarted event
   *
   * @param evt JobStarted event
   */
  def markSubmitted(evt: JobStarted): Unit = {
    submitted += evt.job

    publish(evt)
  }

  /**
   * Mark given Job as Completed
   *
   * @param evt JobCompleted event
   */
  def markCompleted(evt: JobCompleted): Unit = {
    completed += evt.job

    publish(evt)
  }

  /**
   * Mark given Job as Failed
   *
   * @param evt JobFailed event
   */
  def markFailed(evt: JobFailed): Unit = {
    failed += evt.job

    publish(evt)
  }
}

