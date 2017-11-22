package edge.labs.orchestrator

import akka.actor.ActorContext
import akka.testkit.TestProbe
import edge.labs.orchestrator.Supervisor.{Completed, Start}
import edge.labs.orchestrator.dag.DAG
import edge.labs.orchestrator.jobs._
import edge.labs.orchestrator.pipelines._

/**
 * End to End integration test for the Supervisor Actor.
 *
 * Stubs out actual Job execution with random-pause Stub Actors and stubs
 * out the Pipeline Retrieval to use the models found in TestModels
 * for more predictable asserts. All other behavior is PROD-based
 *
 * Created by medge on 6/10/16.
 */
class SupervisorIT extends BaseActorTest with TestModels {

  val workerA = TestProbe()

  // Factory for returning worker stubs
  val stubWorkerFactory = (context: ActorContext, job: Job) => {
    workerA.ref
  }

  // Create real PipelineSupervisor instances but stub out the Worker Creation so Jobs aren't actually executed
  val stubProvider = (context: ActorContext, pipeline: Pipeline) => {
    testActor(
      PipelineSupervisor.props(context.self, pipeline, stubWorkerFactory)
    )
  }

  // Create the test DAG to execute
  val pipelineA = testPipelineA.copy(jobs = Array(testJobA, testJobB, testJobC, testJobD))
  val pipelineB = testPipelineB.copy(jobs = Array(testJobA, testJobB, testJobC))

  val testDAG = new DAG[Pipeline](
    Set(pipelineA, pipelineB),
    Set((pipelineA, pipelineB))
  )

  // Actor under test and an accessor to the underlying implementation for internal state assertions
  val orchestrator = testActor(Supervisor.props(stubProvider, testDAG, testRunDate), "Supervisor")
  val underlyingClass = orchestrator.underlyingActor.asInstanceOf[Supervisor]

  // Reaper watches for termination....
  reaperWatch(orchestrator)

  // Short-hand methods to access the underlying class' relevant internal state
  def submittedPipelines() = underlyingClass.submitted
  def completedPipelines() = underlyingClass.completed

  "Supervisor" should "run a DAG of Pipelines end to end" in {

    orchestrator ! Start()

    // Worker A should receive a StartJob command from a PipelineSupervisor
    workerA.expectMsg(StartJob(testJobA))

    // Since Job A started, PipelineSupervisor should tell Supervisor and trigger an event
    expectEventStreamMessage(PipelineStarted(pipelineA))

    // Supervisor should mark Pipeline A as started
    submittedPipelines() should contain only pipelineA

    // Expect PipelineSupervisor to publish a JobStarted event for A
    workerA.reply(JobStarted(testJobA))
    expectEventStreamMessage(JobStarted(testJobA))

    // Complete Job A. PipelineSupervisor should publish a JobCompleted event
    workerA.reply(JobCompleted(testJobA))
    expectEventStreamMessage(JobCompleted(testJobA))

    // And then kick off the next set of Jobs
    workerA.expectMsg(StartJob(testJobB))
    workerA.expectMsg(StartJob(testJobC))

    // Ensure the JobStarted acknowledgements can occur in any order
    workerA.reply(JobStarted(testJobC))
    expectEventStreamMessage(JobStarted(testJobC))

    workerA.reply(JobStarted(testJobB))
    expectEventStreamMessage(JobStarted(testJobB))

    // Complete Job B and C to kick off the last Job
    workerA.reply(JobCompleted(testJobB))
    expectEventStreamMessage(JobCompleted(testJobB))

    workerA.reply(JobCompleted(testJobC))
    expectEventStreamMessage(JobCompleted(testJobC))

    // Job D should now kick off since dependencies are complete
    workerA.expectMsg(StartJob(testJobD))
    workerA.reply(JobStarted(testJobD))
    expectEventStreamMessage(JobStarted(testJobD))

    // Complete Job D, PipelineSupervisor should now notify Supervisor of a PipelineCompleted event
    workerA.reply(JobCompleted(testJobD))
    expectEventStreamMessage(JobCompleted(testJobD))

    expectEventStreamMessage(PipelineCompleted(pipelineA))
    completedPipelines() should contain only pipelineA

    // Supervisor should now kick off Pipeline B
    // Worker A should receive a StartJob command from a PipelineSupervisor
    workerA.expectMsg(StartJob(testJobA))

    // Since Job A started, PipelineSupervisor should tell Supervisor and trigger an event
    expectEventStreamMessage(PipelineStarted(pipelineB))

    // Supervisor should mark Pipeline B as started
    submittedPipelines() should contain only(pipelineA, pipelineB)

    // Expect PipelineSupervisor to publish a JobStarted event for A
    workerA.reply(JobStarted(testJobA))
    expectEventStreamMessage(JobStarted(testJobA))

    // Complete Job A. PipelineSupervisor should publish a JobCompleted event
    workerA.reply(JobCompleted(testJobA))
    expectEventStreamMessage(JobCompleted(testJobA))

    // And then kick off the next set of Jobs (one for each worker because of JobType)
    workerA.expectMsg(StartJob(testJobB))
    workerA.expectMsg(StartJob(testJobC))

    // Ensure the JobStarted acknowledgements can occur in any order
    workerA.reply(JobStarted(testJobB))
    expectEventStreamMessage(JobStarted(testJobB))

    workerA.reply(JobStarted(testJobC))
    expectEventStreamMessage(JobStarted(testJobC))

    // Complete Job B and C to trigger a Pipeline completion
    workerA.reply(JobCompleted(testJobB))
    expectEventStreamMessage(JobCompleted(testJobB))

    workerA.reply(JobCompleted(testJobC))
    expectEventStreamMessage(JobCompleted(testJobC))

    expectEventStreamMessage(PipelineCompleted(pipelineB))
    completedPipelines() should contain only(pipelineA, pipelineB)

    // Expect a final message signaling the completion of the full run
    expectEventStreamMessage(Completed(testRunDate))

    // And expect Supervisor to clean up after itself as well
    expectReaped(orchestrator)
  }

}
