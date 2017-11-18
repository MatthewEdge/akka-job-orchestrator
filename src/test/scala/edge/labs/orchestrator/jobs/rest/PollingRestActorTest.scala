package edge.labs.orchestrator.jobs.rest

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorRef
import akka.testkit.TestProbe
import edge.labs.orchestrator.jobs.Job
import edge.labs.orchestrator.jobs.rest.PollingRestActor.{PollRequest, PollSuccess, PollTimeout}
import edge.labs.orchestrator.util.rest.RestClient
import edge.labs.orchestrator.{BaseActorTest, TestModels}

import scala.concurrent.duration._

/* @author medge */

/**
 * Unit Tests for the PollingRestActor class
 */
class PollingRestActorTest extends BaseActorTest with TestModels {

  def actorUnderTest(parent: ActorRef, job: Job, restClient: RestClient) = testActor(PollingRestActor.props(parent, job, restClient))

  it should "poll the correct polling endpoint until the pollFor attribute is returned" in {
    val pollingRestClient = new RestClient {
      private val counter: AtomicInteger = new AtomicInteger(0)

      override def get(url: String, queryParams: Option[Map[String, String]]): (Int, String) = {
        // For the polling endpoint
        if(url.contains("/pollA")) {

          // The first couple requests will return a "Running" status
          if (counter.get() < 3) {
            counter.incrementAndGet()

            (200, "{\"status\": \"Running\"}")
          } else {
            // The final request will return a Success
            (200, "{\"status\": \"Success\"}")
          }
        } else {
          (404, "Invalid endpoint for this test ya dingus!") // You goofed if this happens ;)
        }
      }

      // This method should not be needed but we have to create it because interfaces
      override def post(url: String, queryParams: Option[Map[String, String]], body: Option[String]): (Int, String) = {(500, "SHOULDN'T CALL THIS")}
    }

    val pollingJob = testJobA.copy(
      pollEndpoint = Some("/pollA"),
      pollMethod = Some("GET"),
      pollFor = Some(Map("status" -> "Success")),
      pollFailure = Some(Map("status" -> "Failed"))
    )

    val pollParent = TestProbe()
    val actor = actorUnderTest(pollParent.ref, pollingJob, pollingRestClient)

    actor ! PollRequest(10.seconds, 250.milliseconds)

    // Within 3 seconds we should receive a PollCompleted message
    pollParent.expectMsg(3.seconds, PollSuccess(testRunDate, pollingJob))
  }

  it should "return a PollTimeout if pollFor is not returned in the allotted time frame" in {
    val pollingRestClient = MockRestClient(200, "{\"status\": \"Running\"}") // Always return running

    val pollingJob = testJobA.copy(
      pollEndpoint = Some("/pollA"),
      pollMethod = Some("GET"),
      pollFor = Some(Map("status" -> "Success")),
      pollFailure = Some(Map("status" -> "Failed"))
    )

    val pollParent = TestProbe()
    val actor = actorUnderTest(pollParent.ref, pollingJob, pollingRestClient)

    // After 1 seconds the request should time out
    actor ! PollRequest(1.seconds, 2.seconds)

    // This should trigger a PollTimeout message
    pollParent.expectMsg(3.seconds, PollTimeout(testRunDate, pollingJob))
  }

  it should "not handle an unknown message" in {
    val actor = actorUnderTest(null, null, null)

    expectUnhandled("WHAT'S UP DOC?", actor)
  }

}
