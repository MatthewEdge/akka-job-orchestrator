package edge.labs.orchestrator.pipelines

import akka.actor.{ActorContext, Props}
import akka.testkit.TestProbe
import edge.labs.orchestrator.jobs._
import edge.labs.orchestrator.{BaseActorTest, DagHelpers, TestModels}

/* @author medge */

/**
 * End to End integration test for the PipelineSupervisor Actor.
 * Stubs out the Worker Actor behavior (but still tests Job Actor Selection)
 *
 * Note: all test models (including the test DAG) come from TestModels
 *
 * Created by medge on 6/10/16.
 */
class PipelineSupervisorIT extends BaseActorTest with TestModels with DagHelpers {

  // Two Worker Stubs for different Job Types
  val parentStub = TestProbe()

  val workerA = TestProbe()

  // Factory for returning worker stubs
  val stubWorkerFactory = (context: ActorContext, job: Job) => {
    workerA.ref
  }

  val testPipeline = testPipelineA.copy(jobs = Array(testJobA, testJobB, testJobC, testJobD))

  // Actor under test and an accessor to the underlying implementation for internal state assertions
  val pipelineSupervisor = testActor(Props(new PipelineSupervisor(parentStub.ref, testPipeline, stubWorkerFactory)))
  val underlyingClass = pipelineSupervisor.underlyingActor.asInstanceOf[PipelineSupervisor]

  // Reaper watches for termination....
  reaperWatch(pipelineSupervisor)

  // Short-hand methods to access the underlying class' relevant internal state
  def submittedJobs() = underlyingClass.submitted
  def completedJobs() = underlyingClass.completed

  "PipelineSupervisor" should "run a Pipeline end to end" in {

    // First, make sure the correct DAG is assigned
    assertDagsMatch[String, Job](underlyingClass.dag, testJobDAG)

    // Kick off all Start Jobs (Job A)
    pipelineSupervisor ! StartPipeline()

    // Expect that the correct Worker received the request
    workerA.expectMsg(StartJob(testJobA))
    workerA.reply(JobStarted(testJobA))

    // Ensure only Job A was submitted
    expectEventStreamMessage(JobStarted(testJobA))
    submittedJobs() should contain only testJobA
    completedJobs() shouldBe empty

    // Complete Job A
    workerA.reply(JobCompleted(testJobA))
    expectEventStreamMessage(JobCompleted(testJobA))

    completedJobs() should contain only testJobA

    // Jobs B and C should now kick off
    workerA.expectMsg(StartJob(testJobB))
    workerA.reply(JobStarted(testJobB))

    expectEventStreamMessage(JobStarted(testJobB))

    // Only Job A and Job B should be submitted at this point
    submittedJobs() should contain only(testJobA, testJobB)

    workerA.expectMsg(StartJob(testJobC))
    workerA.reply(JobStarted(testJobC))

    expectEventStreamMessage(JobStarted(testJobC))

    // Only Job A, Job B, and Job C should be submitted at this point
    submittedJobs() should contain only(testJobA, testJobB, testJobC)

    // Job A should still be the only completed Job
    completedJobs() should contain only testJobA

    // Complete Job B. Job C is not done so Job D should NOT kick off
    workerA.reply(JobCompleted(testJobB))
    expectEventStreamMessage(JobCompleted(testJobB))

    completedJobs() should contain only(testJobA, testJobB)
    submittedJobs() shouldNot contain(testPipelineD)

    // Complete Pipeline C. Pipeline D should now be submitted
    workerA.reply(JobCompleted(testJobC))
    expectEventStreamMessage(JobCompleted(testJobC))

    completedJobs() should contain only(testJobA, testJobB, testJobC)

    // Kick of Pipeline D
    workerA.expectMsg(StartJob(testJobD))
    workerA.reply(JobStarted(testJobD))
    expectEventStreamMessage(JobStarted(testJobD))

    submittedJobs() should contain only(testJobA, testJobB, testJobC, testJobD)

    // Completed Jobs should still only contain A, B, and C
    completedJobs() should contain only(testJobA, testJobB, testJobC)

    // Exit node completed, finish the DAG
    workerA.reply(JobCompleted(testJobD))
    expectEventStreamMessage(JobCompleted(testJobD))

    completedJobs() should contain only(testJobA, testJobB, testJobC, testJobD)

    // Expect a final message to be sent to the parent signaling the completion of the Pipeline's execution
    // TODO why is this catching a PipelineStarted event?
    // parentStub.expectMsg(PipelineCompleted(testPipeline))

    // And expect PipelineSupervisor to clean up after itself
    expectReaped(pipelineSupervisor)
  }

}
