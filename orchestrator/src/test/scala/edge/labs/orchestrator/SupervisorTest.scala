package edge.labs.orchestrator

import akka.actor.ActorContext
import akka.testkit.TestProbe
import edge.labs.orchestrator.Supervisor.{Completed, Failed, Start}
import edge.labs.orchestrator.dag.DAG
import edge.labs.orchestrator.pipelines._

/**
 * Unit test to run the Supervisor end to end without calling
 * actual services. Work Actors are stubbed out
 *
 * Created by medge on 6/3/16.
 */
class SupervisorTest extends BaseActorTest with TestModels {

  // Stub for the PipelineSupervisor children Supervisor creates
  val testProbe = TestProbe()

  // Stub for the DAG Provider
  val stubDagProvider = TestProbe()

  def testDag: DAG[Pipeline] = new DAG[Pipeline](Set(testPipelineA), Set.empty)

  /**
   * Short-hand for the createActor() call
   */
  def actorUnderTest(dag: DAG[Pipeline] = testDag) = {
    val actorCreatorStub = (context: ActorContext, pipeline: Pipeline) => testProbe.ref

    testActor(Supervisor.props(actorCreatorStub, dag, testRunDate))
  }

  "Start() command" should "submit the test Pipeline to a child Actor" in {
    val supervisor = actorUnderTest()

    supervisor ! Start()

    // TestProbe should receive a StartPipeline
    testProbe.expectMsg(StartPipeline())
  }

  "PipelineStarted() event from a child Actor" should "emit a PipelineStarted event" in {
    val supervisor = actorUnderTest()

    supervisor ! PipelineStarted(testPipelineA)

    // Expect a PipelineStarted event on the Event Stream
    expectEventStreamMessage(PipelineStarted(testPipelineA))
  }

  "PipelineCompleted() event from a child Actor" should "emit a PipelineCompleted event" in {
    val supervisor = actorUnderTest()

    supervisor ! PipelineCompleted(testPipelineA)

    // Expect a PipelineStarted event on the Event Stream
    expectEventStreamMessage(PipelineCompleted(testPipelineA))
  }

  "PipelineCompleted() for the last exit node in the DAG" should "emit a Completed event" in {
    val supervisor = actorUnderTest()

    supervisor ! PipelineCompleted(testPipelineA)

    // Expect a PipelineStarted event on the Event Stream
    expectEventStreamMessage(Completed(testRunDate))
  }

  "Supervisor" should "halt the run immediately on failure if the continueOnFailure flag is false" in {
    val newTestDag: DAG[Pipeline] = DAG.from[String, Pipeline](
      Set(
        testPipelineA,
        testPipelineB
      )
    )

    val supervisor = actorUnderTest(newTestDag)

    supervisor ! Start()
    supervisor ! PipelineFailed(testPipelineA, "SIMULATED FAILURE")

    // Validate the message contains the correct information
    expectEventStreamMessage(Failed(testRunDate, s"There were Pipeline failures: ${testPipelineA.toString}"))
  }

  "For continueOnFailure[true], Supervisor" should "trigger a Failed only after all Pipelines run and at least one Pipeline failed" in {
    val pipelineAWithContinue = testPipelineA.copy(continueOnFailure = true)

    val newTestDag: DAG[Pipeline] = DAG.from[String, Pipeline](
      Set(pipelineAWithContinue, testPipelineB, testPipelineC, testPipelineD)
    )

    val supervisor = actorUnderTest(newTestDag)

    supervisor ! Start()

    supervisor ! PipelineFailed(pipelineAWithContinue, "SIMULATED FAILURE")
    supervisor ! PipelineCompleted(testPipelineB)
    supervisor ! PipelineCompleted(testPipelineC)

    // Make sure, at this point, that the failed Pipeline A is the only failed Pipeline tracked
    val instance = supervisor.underlyingActor.asInstanceOf[Supervisor]
    instance.failed should contain only pipelineAWithContinue

    // Finishing the Pipeline should send a PipelineFailed message back to the parent
    supervisor ! PipelineCompleted(testPipelineD)

    // Validate the message contains the correct information
    expectEventStreamMessage(Failed(testRunDate, s"There were Pipeline failures: ${pipelineAWithContinue.toString}"))
  }

  /**
   * End to End execution with PipelineSupervisor stubbed out
   */
  "Supervisor" should "run a DAG of Pipelines end to end" in {

    // Stub out the PipelineSupervisor
    val childStub = TestProbe()
    val stubProvider = (context: ActorContext, pipeline: Pipeline) => childStub.ref

    // Actor under test and an accessor to the underlying implementation for internal state assertions
    val orchestrator = testActor(Supervisor.props(stubProvider, testPipelineDAG, testRunDate))
    val underlyingClass = orchestrator.underlyingActor.asInstanceOf[Supervisor]

    // Reaper watches for termination....
    reaperWatch(orchestrator)

    // Short-hand methods to access the underlying class' relevant internal state
    def submittedPipelines() = underlyingClass.submitted
    def completedPipelines() = underlyingClass.completed

    // Kick off all Start Nodes (Pipeline A)
    orchestrator ! Start()

    childStub.expectMsg(StartPipeline())
    childStub.reply(PipelineStarted(testPipelineA))

    expectEventStreamMessage(PipelineStarted(testPipelineA))
    submittedPipelines() should contain only testPipelineA

    // Complete Pipeline A
    childStub.reply(PipelineCompleted(testPipelineA))
    expectEventStreamMessage(PipelineCompleted(testPipelineA))

    completedPipelines() should contain only testPipelineA

    // Pipeline B and Pipeline C should now start
    childStub.expectMsgAllOf(StartPipeline(), StartPipeline())

    childStub.reply(PipelineStarted(testPipelineB))
    expectEventStreamMessage(PipelineStarted(testPipelineB))

    // Only Pipeline A and Pipeline B should be submitted at this point
    submittedPipelines() should contain only(testPipelineA, testPipelineB)

    childStub.reply(PipelineStarted(testPipelineC))
    expectEventStreamMessage(PipelineStarted(testPipelineC))

    // Only Pipeline A, Pipeline B, and Pipeline C should be submitted at this point
    submittedPipelines() should contain only(testPipelineA, testPipelineB, testPipelineC)

    // Pipeline A should still be the only completed Pipeline
    completedPipelines() should contain only testPipelineA

    // Complete Pipeline B. Pipeline C is not done so Pipeline D should NOT kick off
    childStub.reply(PipelineCompleted(testPipelineB))
    expectEventStreamMessage(PipelineCompleted(testPipelineB))

    completedPipelines() should contain only(testPipelineA, testPipelineB)
    submittedPipelines() shouldNot contain(testPipelineD)

    // Complete Pipeline C. Pipeline D should now be submitted
    childStub.reply(PipelineCompleted(testPipelineC))
    expectEventStreamMessage(PipelineCompleted(testPipelineC))

    completedPipelines() should contain only(testPipelineA, testPipelineB, testPipelineC)

    // Kick of Pipeline D
    childStub.expectMsg(StartPipeline())
    childStub.reply(PipelineStarted(testPipelineD))
    expectEventStreamMessage(PipelineStarted(testPipelineD))

    submittedPipelines() should contain only(testPipelineA, testPipelineB, testPipelineC, testPipelineD)

    // Exit node completed, finish the DAG
    childStub.reply(PipelineCompleted(testPipelineD))
    expectEventStreamMessage(PipelineCompleted(testPipelineD))

    completedPipelines() should contain only(testPipelineA, testPipelineB, testPipelineC, testPipelineD)

    // Expect a final message signaling the completion of the full run
    expectEventStreamMessage(Completed(testRunDate))

    // And expect Supervisor to clean up after itself
    expectReaped(orchestrator)
  }

  it should "not handle an unknown message" in {
    val actor = actorUnderTest()

    expectUnhandled("WHAT'S UP DOC?", actor)
  }

}
