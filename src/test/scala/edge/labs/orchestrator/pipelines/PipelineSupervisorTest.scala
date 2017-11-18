package edge.labs.orchestrator.pipelines

import akka.actor.{ActorContext, Props}
import akka.testkit.{TestActorRef, TestProbe}
import edge.labs.orchestrator.jobs._
import edge.labs.orchestrator.{BaseActorTest, TestModels}

/* @author medge */

/**
 * Unit test to run the Supervisor end to end without calling
 * actual services. Work Actors are stubbed out
 *
 * Created by medge on 6/3/16.
 */
class PipelineSupervisorTest extends BaseActorTest with TestModels {

  // Stub to listen to messages intended for the PipelineSupervisor parent
  val parentStub = TestProbe()

  // Stub for the Worker Actors that would actually submit the Jobs
  val jobWorkerStub = new TestProbe(system)

  val testPipeline = testPipelineA.copy(jobs = Array(testJobA, testJobB, testJobC, testJobD))

  /**
   * Create an instance of PipelineSupervisor with the default testPipeline
   */
  def actorUnderTest(): TestActorRef[PipelineSupervisor] = {
    actorUnderTest(testPipeline)
  }

  /**
   * Create an instance of PipelineSupervisor with the testProbe injected as it's parent
   * and an instance of jobWorkerStub injected for the Worker Actors. The given Pipeline
   * will be executed
   */
  def actorUnderTest(testPipeline: Pipeline): TestActorRef[PipelineSupervisor] = {
    val actorCreatorStub = (context: ActorContext, job: Job) => jobWorkerStub.ref

    testActor(Props(new PipelineSupervisor(parentStub.ref, testPipeline, actorCreatorStub))).asInstanceOf[TestActorRef[PipelineSupervisor]]
  }

  "StartPipeline() command" should "kick off the first set of Jobs in the DAG" in {
    val supervisor = actorUnderTest()

    supervisor ! StartPipeline()

    // Worker stub should receive a StartJob for each start node
    testJobDAG.startNodes().foreach(job => jobWorkerStub.expectMsg(StartJob(job)))

    // Parent should receive a PipelineStarted event
    parentStub.expectMsg(PipelineStarted(testPipeline))
  }

  "startJob()" should "send a PipelineFailed message to the parent if Actor Creation fails" in {
    val failingCreator = (context: ActorContext, job: Job) => throw new RuntimeException("Test Actor Creator Failure")
    val actor = testActor(Props(new PipelineSupervisor(parentStub.ref, testPipeline, failingCreator)))
    val instance = actor.underlyingActor.asInstanceOf[PipelineSupervisor]

    instance.startJob(testJobA)
    parentStub.expectMsgClass(classOf[PipelineFailed])
  }

  "JobStarted() event from child actor" should "emit a JobStarted event" in {
    val supervisor = actorUnderTest()

    supervisor ! StartPipeline()

    // Expect a PipelineStarted event on the Event Stream
    parentStub.expectMsg(PipelineStarted(testPipeline))

    // Expect a JobStarted event for each start node
    testJobDAG.startNodes().foreach { job =>
      supervisor ! JobStarted(job)

      expectEventStreamMessage(JobStarted(job))
    }

  }

  "JobCompleted() event from a child Actor" should "emit a JobCompleted event" in {
    val supervisor = actorUnderTest()

    supervisor ! JobCompleted(testJobA)

    // Expect a PipelineStarted event on the Event Stream
    expectEventStreamMessage(JobCompleted(testJobA))
  }

  "JobCompleted() for the last exit node in the DAG" should "emit a PipelineCompleted event" in {
    val supervisor = actorUnderTest()

    // For DeathWatch
    reaperWatch(supervisor)

    // Seed other Jobs to ensure completion triggers
    Array(testJobA, testJobB, testJobC).foreach(job => supervisor ! JobCompleted(job))

    supervisor ! JobCompleted(testJobD)

    // Expect a PipelineStarted event on the Event Stream
    parentStub.expectMsg(PipelineCompleted(testPipeline))

    // Make sure PipelineSupervisor cleans up after itself
    expectReaped(supervisor)
  }

  "JobFailed()" should "emit a JobFailed event to the event stream" in {
    val supervisor = actorUnderTest()

    supervisor ! JobFailed(testJobA, "SIMULATED FAILURE")

    expectEventStreamMessage(JobFailed(testJobA, "SIMULATED FAILURE"))
  }

  "if continueOnFailure is true, PipelineSupervisor" should "trigger a PipelineFailed after all Jobs run and at least one Job failed" in {
    val supervisor = actorUnderTest()

    val jobAWithContinue = testJobA.copy(continueOnFailure = true)

    supervisor ! JobFailed(jobAWithContinue, "SIMULATED FAILURE")
    supervisor ! JobCompleted(testJobB)
    supervisor ! JobCompleted(testJobC)

    // Make sure, at this point, that the failed Job A is the only failed Job tracked
    val instance = supervisor.underlyingActor
    instance.failed should contain only jobAWithContinue

    // Finishing the Pipeline should send a PipelineFailed message back to the parent
    supervisor ! JobCompleted(testJobD)

    // Validate the message contains the correct information
    val result = parentStub.expectMsgClass(classOf[PipelineFailed])
    result.pipeline shouldEqual testPipeline
    result.reason should include regex """There were Job failures"""
    result.reason should include regex testJobA.id
    result.reason shouldNot include regex testJobB.id
    result.reason shouldNot include regex testJobC.id
    result.reason shouldNot include regex testJobD.id
  }

  it should "not handle an unknown message" in {
    val actor = actorUnderTest()

    expectUnhandled("WHAT'S UP DOC?", actor)
  }

}

