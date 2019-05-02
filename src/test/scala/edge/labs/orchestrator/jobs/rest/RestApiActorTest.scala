package edge.labs.orchestrator.jobs.rest

import akka.actor.ActorRef
import akka.testkit.TestProbe
import edge.labs.orchestrator.jobs._
import edge.labs.orchestrator.jobs.rest.PollingRestActor.PollTimeout
import edge.labs.orchestrator.{BaseActorTest, TestModels}

/**
 * Unit tests for the RestApiActor.
 *
 * NOTE:
 *    testRunDate is tested by the mockGetwithResponse() and mockPostWithResponse() methods
 *
 * Created by medge on 6/4/16.
 */
class RestApiActorTest extends BaseActorTest with TestModels {

  /**
   * Return a mocked RestClient GET function which returns the expected Http Status Code/Response Body
   */
  def mockGetWithResponse(expectedStatusCode: Int, expectedHttpBody: String = "") = {
    (url: String, queryParams: Option[Map[String, String]]) => {

      // Implicitly assert that runDate is appended
      url should endWith(testRunDate)

      (expectedStatusCode, "GET " + expectedHttpBody)
    }
  }

  /**
   * Return a mocked RestClient POST function which returns the expected Http Status Code/Response Body
   */
  def mockPostWithResponse(expectedStatusCode: Int, expectedHttpBody: String = "") = {
    (url: String, postBody: String, queryParams: Option[Map[String, String]]) => {
      // Implicitly assert that runDate is appended
      url should endWith(testRunDate)

      (expectedStatusCode, "POST " + expectedHttpBody)
    }
  }

  // Parent stub
  val mockParent = TestProbe()

  // Short-hand for the createActor() call
  def actorUnderTest(restClient: RestClient) = testActor(RestApiActor.props(mockParent.ref, restClient))
  def actorUnderTest(parent: ActorRef, restClient: RestClient) = testActor(RestApiActor.props(parent, restClient))

  // Short-hand to get at the underlying Actor
  def actorInstance(restClient: RestClient) = actorUnderTest(restClient).underlyingActor.asInstanceOf[RestApiActor]

  "A HTTP 200 for a get() call" should "return a JobCompleted message to the parent" in {
    val happyPathClient = MockRestClient(statusCode = 200)
    val actor = actorUnderTest(happyPathClient)

    actor ! StartJob(testJobA)
    mockParent.expectMsg(JobStarted(testJobA))
    mockParent.expectMsg(JobCompleted(testJobA))
  }

  "A HTTP 404 for a get() call" should "return a JobFailed message to the parent" in {
    val notFoundClient = MockRestClient(statusCode = 404)
    val actor = actorUnderTest(notFoundClient)

    actor ! StartJob(testJobA)
    mockParent.expectMsg(JobStarted(testJobA))

    val response = mockParent.expectMsgClass(classOf[JobFailed])
    response.job shouldEqual testJobA
    response.reason should include regex """Http Code: 404"""
  }

  "A HTTP 500 for a get() call" should "return a JobFailed message to the parent" in {
    val failedExecClient = MockRestClient(statusCode = 500)
    val actor = actorUnderTest(failedExecClient)

    actor ! StartJob(testJobA)
    mockParent.expectMsg(JobStarted(testJobA))

    val response = mockParent.expectMsgClass(classOf[JobFailed])
    response.job shouldEqual testJobA
    response.reason should include regex """Http Code: 500"""
  }

  "An unknown HTTP Status for a get() call" should "return a JobFailed message to the parent" in {
    val failedExecClient = MockRestClient(statusCode = 999)
    val actor = actorUnderTest(failedExecClient)

    actor ! StartJob(testJobA)
    mockParent.expectMsg(JobStarted(testJobA))

    val response = mockParent.expectMsgClass(classOf[JobFailed])
    response.job shouldEqual testJobA
    response.reason should include regex """Unknown response returned:\n\tHttp Code: 999"""
  }

  "A malformed Job message" should "return a JobFailed message" in {
    val actor = actorUnderTest(null)

    val invalidJob = testJobA.copy(endpoint = "") // endpoint empty == failure case
    actor ! StartJob(invalidJob)

    mockParent.expectMsgClass(classOf[JobFailed])
  }

  "A malformed Job message" should "trigger the actor to Terminate" in {
    val actor = actorUnderTest(null)
    val probe = TestProbe()

    probe watch actor

    val invalidJob = testJobA.copy(endpoint = "")
    actor ! StartJob(invalidJob)

    probe.expectTerminated(actor)
  }

  "validateJob()" should "return an empty String for a proper Job" in {
    val job = testJobA

    actorInstance(null).canProcess(job) shouldBe empty
  }

  "validateJob()" should "return an error if the endpoint key is missing" in {
    val job = testJobA.copy(endpoint = "")

    actorInstance(null).canProcess(job) should include regex s"""Job ${job.id} is missing the 'endpoint' key!"""
  }

  "validateJob()" should "return an error if the METHOD key is missing" in {
    val job = testJobA.copy(method = "")

    actorInstance(null).canProcess(job) should include regex s"""Job ${job.id} is missing the 'method' key!"""
  }

  "A Polling REST Job" should "create a child Actor to poll the given URL" in {
    // Ensure the expected response is returned to trigger the Success
    val pollingRestClient = MockRestClient(200, "{\"status\": \"Success\"}")

    val pollingJob = testJobA.copy(
      pollEndpoint = Some("/pollA"),
      pollMethod = Some("GET"),
      pollFor = Some(Map("status" -> "Success")),
      pollFailure = Some(Map("status" -> "Failed"))
    )

    val pollParent = TestProbe()
    val actor = actorUnderTest(pollParent.ref, pollingRestClient)
    actor ! StartJob(pollingJob)

    pollParent.expectMsg(JobStarted(pollingJob))
    pollParent.expectMsg(JobCompleted(pollingJob))
  }

  "A PollTimeout message" should "trigger a JobFailed message to be sent to the parent" in {
    val failedExecClient = MockRestClient(500, "Failed Execution?")
    val actor = actorUnderTest(failedExecClient)

    actor ! PollTimeout(testRunDate, testJobA)

    mockParent.expectMsgClass(classOf[JobFailed])
  }

  "A Polling REST Job" should "return a PollFailure if the REST call fails" in {
    // Return a HTTP Failure (HTTP 500). Should trigger a failure even though the httpBody says success
    val pollingRestClient = MockRestClient(500, "{\"status\": \"Success\"}")

    val pollingJob = testJobA.copy(
      pollEndpoint = Some("/pollA"),
      pollMethod = Some("GET"),
      pollFor = Some(Map("status" -> "Success")),
      pollFailure = Some(Map("status" -> "Failed"))
    )

    val pollParent = TestProbe()
    val actor = actorUnderTest(pollParent.ref, pollingRestClient)
    actor ! StartJob(pollingJob)

    pollParent.expectMsg(JobStarted(pollingJob))
    pollParent.expectMsgClass(classOf[JobFailed])
  }

  "A Polling REST Job" should "return a PollFailure if the failure response is returned" in {
    // Return a HTTP Success (HTTP 200). Should trigger a failure because of the response body
    val pollingRestClient = MockRestClient(200, "{\"status\": \"Failed\"}")

    val pollingJob = testJobA.copy(
      pollEndpoint = Some("/pollA"),
      pollMethod = Some("GET"),
      pollFor = Some(Map("status" -> "Success")),
      pollFailure = Some(Map("status" -> "Failed"))
    )

    val pollParent = TestProbe()
    val actor = actorUnderTest(pollParent.ref, pollingRestClient)
    actor ! StartJob(pollingJob)

    pollParent.expectMsg(JobStarted(pollingJob))
    pollParent.expectMsgClass(classOf[JobFailed])
  }

  "A Polling REST Job" should "return a PollFailure if the pollMethod property is missing" in {
    // Return a HTTP Success (HTTP 200). Should trigger a failure because of the response body
    val pollingRestClient = MockRestClient(200, "{\"status\": \"Success\"}")

    val pollParent = TestProbe()
    val actor = actorUnderTest(pollParent.ref, pollingRestClient)

    // pollMethod missing
    val pollingJobWithoutPollMethod = testJobA.copy(
      pollEndpoint = Some("/pollA"),
      //pollMethod = Some("GET"),
      pollFor = Some(Map("status" -> "Success")),
      pollFailure = Some(Map("status" -> "Failed"))
    )

    actor ! StartJob(pollingJobWithoutPollMethod)

    pollParent.expectMsg(JobStarted(pollingJobWithoutPollMethod))
    pollParent.expectMsgClass(classOf[JobFailed])
  }

  "A Polling REST Job" should "return a PollFailure if the pollFor property is missing" in {
    // Return a HTTP Success (HTTP 200). Should trigger a failure because of the response body
    val pollingRestClient = MockRestClient(200, "{\"status\": \"Success\"}")

    val pollParent = TestProbe()
    val actor = actorUnderTest(pollParent.ref, pollingRestClient)

    // pollFor missing
    val pollingJobWithoutPollFor = testJobA.copy(
      pollEndpoint = Some("/pollA"),
      pollMethod = Some("GET"),
      //pollFor = Some(Map("status" -> "Success")),
      pollFailure = Some(Map("status" -> "Failed"))
    )

    actor ! StartJob(pollingJobWithoutPollFor)

    pollParent.expectMsg(JobStarted(pollingJobWithoutPollFor))
    pollParent.expectMsgClass(classOf[JobFailed])
  }

  it should "not handle an unknown message" in {
    val actor = actorUnderTest(null)

    expectUnhandled("WHAT'S UP DOC?", actor)
  }

}
