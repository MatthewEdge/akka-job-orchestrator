package edge.labs.orchestrator.rest

import edge.labs.orchestrator.Supervisor.{Completed, Failed}
import edge.labs.orchestrator._
import edge.labs.orchestrator.jobs.{JobCompleted, JobFailed, JobStarted}
import edge.labs.orchestrator.pipelines.{PipelineCompleted, PipelineFailed}

/* @author medge */

/**
 * Unit Tests for the Status object - specifically the derivation logic around
 * deriving a status from a given sequence of Event objects
 */
class RunnerStatusTest extends BaseTest with TestModels {

  it should "produce an empty Status object when only given a runDate and status" in {
    val result = Status(testRunDate, "TEST")

    result.jobEvents shouldBe empty
    result.pipelineEvents shouldBe empty
    result.runDate shouldBe testRunDate
    result.status shouldBe "TEST"
  }

  it should "produce a Not Running event for an empty Events array" in {
    val result = Status(testRunDate, Array.empty[Event])

    result.status shouldEqual StatusEnum.NOT_RUNNING
  }

  it should "produce a Running event if the events array has no success/failure events" in {
    val result = Status(
      testRunDate,
      Array(JobStarted(testJobA), JobCompleted(testJobA)) // Job and Pipeline Completed events should not trigger a Completed status
    )

    result.status shouldEqual StatusEnum.RUNNING
  }

  it should "produce a Completed event if the events array contains a Completed event" in {
    val result = Status(
      testRunDate,
      Array.apply[Event](
        JobStarted(testJobA),
        JobCompleted(testJobA),
        PipelineFailed(testPipelineA, "TEST"), // Not likely, but just to make sure it does not trigger a Failure
        Completed(testRunDate)
      )
    )

    result.status shouldEqual StatusEnum.COMPLETED
  }

  it should "produce a Failed status if the events array contains a Failed event" in {
    val result = Status(
      testRunDate,
      Array.apply[Event](
        JobStarted(testJobA),
        JobCompleted(testJobA),
        PipelineCompleted(testPipelineA),
        Failed(testRunDate, "TEST")
      )
    )

    result.status shouldEqual StatusEnum.FAILED
  }

  it should "produce a Failed status if the events array contains a PipelineFailed event" in {
    val result = Status(
      testRunDate,
      Array.apply[Event](
        JobStarted(testJobA),
        JobCompleted(testJobA),
        PipelineFailed(testPipelineA, "TEST FAILURE")
      )
    )

    result.status shouldEqual StatusEnum.FAILED
  }

  it should "produce a Failed status if the events array contains a JobFailed event" in {
    val result = Status(
      testRunDate,
      Array.apply[Event](
        JobStarted(testJobA),
        JobCompleted(testJobA),
        PipelineCompleted(testPipelineA),
        JobFailed(testJobB, "TEST FAILURE")
      )
    )

    result.status shouldEqual StatusEnum.FAILED
  }

}
