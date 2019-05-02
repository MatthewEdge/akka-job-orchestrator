package edge.labs.orchestrator.jobs.rest

import akka.actor.{ActorRef, Cancellable, Props}
import edge.labs.orchestrator.actor.BaseActor
import edge.labs.orchestrator.jobs.Job
import edge.labs.orchestrator.jobs.rest.PollingRestActor._
import edge.labs.orchestrator.json.JsonSupport
import edge.labs.orchestrator.{Command, Event}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/* @author medge */

object PollingRestActor {

  def props(parent: ActorRef, job: Job, restClient: RestClient) = Props(new PollingRestActor(parent, job, restClient))

  /**
   * Submit a REST Job using the given restClient. Centralizes logic around path construction and REST Client
   * adaptation.
   *
   * Assumes that all poll* variables exist and have been previously validated
   *
   * @param job Job
   * @param restClient RestClient
   * @return (Int, String) httpCode, httpBody
   */
  def submitPollingJob(job: Job, restClient: RestClient): (Int, String) = {
    val url = s"${job.baseUrl}/${job.pollEndpoint.get}"
    val httpMethod = job.pollMethod.get.toUpperCase()
    val maybePollQueryParams = job.pollQueryParams

    // Submit the REST request
    restClient.submitRequest(job.runDate, url, httpMethod, maybePollQueryParams)
  }


  /****************************************************
   *        Messages this Actor handles
   ***************************************************/

  /**
   * Command to poll for an expected value
   *
   * @param maxDuration FiniteDuration Maximum amount of time to poll for the expected value
   * @param delay FiniteDuration delay time between pollings. Defaults to 30 seconds
   */
  case class PollRequest(
    maxDuration: FiniteDuration = 1.hour,
    delay: FiniteDuration = 30.seconds
  ) extends Command

  sealed trait PollEvent extends Event

  /* Poll Success Event */
  case class PollSuccess(runDate: String, job: Job) extends PollEvent {
    def this(job: Job) = this(job.runDate, job)
  }

  object PollSuccess {
    def apply(job: Job) = new PollSuccess(job)
  }

  /* Poll Failed Event */
  case class PollFailure(runDate: String, job: Job, reason: String) extends PollEvent {
    def this(job: Job, reason: String) = this(job.runDate, job, reason)
  }

  object PollFailure {
    def apply(job: Job, reason: String) = new PollFailure(job, reason)
  }

  /* Poll Timeout Event for if maxDuration is hit */
  case class PollTimeout(runDate: String, job: Job) extends PollEvent {
    def this(job: Job) = this(job.runDate, job)
  }

  object PollTimeout {
    def apply(job: Job) = new PollTimeout(job)
  }

}

/**
 * Actor responsible for polling the given Job's pollEndpoint for the Job's pollFor result.
 *
 * Handles the logic of converting pollFor to JSON and comparing the REST response body
 */
class PollingRestActor(parent: ActorRef, job: Job, restClient: RestClient) extends BaseActor with JsonSupport {

  // Internal message sent by this Actor to itself. Used for scheduling a Poll "retry"
  sealed case class DoPoll(poll: PollRequest)

  // Timeout trigger
  var timeout: Cancellable = _
  implicit val schedulerEC: ExecutionContext = context.dispatcher

  override def receive: Receive = {

    // Initial request
    case poll: PollRequest =>
      val errors = validateRequest()

      if(errors.isEmpty) {

        // Schedule timeout trigger to be sent to the parent if Polling never completes
        timeout = taskScheduler.scheduleOnce(poll.maxDuration, self, PollTimeout(job))

        // Start polling
        self ! DoPoll(poll)
      } else {
        parent ! PollFailure(job, s"Invalid Polling Job Request:\n$errors")
      }

    // Poll "loop"
    case DoPoll(poll) =>

      log.info(s"Polling ${job.id} with $poll")

      val (httpCode, httpBody) = submitPollingJob(job, restClient)
      val response = jsonToMap[String, Any](httpBody)

      log.debug(s"Response received: $response")

      if(nonSuccessCode(httpCode)) {
        parent ! PollFailure(job, s"REST call failed! Received HTTP $httpCode - $httpBody")
        shutdown()
      }
      else {
        parsePollResponse(response) match {

          // Polling completed. Notify parent and shutdown
          case Some(pollEvent) =>
            parent ! pollEvent
            shutdown()

          // Polling continues
          case None =>
            taskScheduler.scheduleOnce(poll.delay, self, DoPoll(poll))
        }
      }

    // Timeout hit. Forward to parent and shutdown
    case timeout: PollTimeout =>
      parent ! timeout
      shutdown()

    case m @ _ =>
      log.warning(s"Unrecognized message sent to PollingRestActor: $m")
      unhandled(m)

  }

  /**
   * Validate that the correct poll properties exist on the given Job
   *
   * @return String list of errors if any. Empty string if valid Polling Job
   */
  def validateRequest(): String = {
    val errs = new StringBuilder

    // This should NOT happen because this key is what triggers a Polling job in RestApiActor to begin with
    if(job.pollEndpoint.isEmpty)
      errs.append("pollEndpoint key is missing!")

    if(job.pollMethod.isEmpty)
      errs.append("pollMethod key is missing!")

    if(job.pollFor.isEmpty)
      errs.append("pollMethod key is missing!")

    // pollFailure is optional so we don't validate that here

    errs.toString()
  }

  // In this world - HTTP 200 or bust
  /**
   * @param httpCode Int
   * @return Boolean true if httpCode is not 200
   */
  def nonSuccessCode(httpCode: Int): Boolean = httpCode != 200

  /**
   * Parse the given JSON Response Map for a success or failure.
   *
   * @param response Map[String, String] parsed JSON as a Map
   * @return Option[PollEvent] Some(event) if polling completed. None if not
   */
  def parsePollResponse(response: Map[String, Any]): Option[PollEvent] = {

    // These values should have already been validated (convert to lower case for equality check
    val pollFor = job.pollFor.get
    val pollFailure = job.pollFailure.get

    // If response contains the pollFailure (key, value) pair, trigger Poll Failure
    val failureReturned =
      pollFailure.exists { case (failKey, failVal) =>
        response.exists { case (respKey, respVal) =>
          failKey.equalsIgnoreCase(respKey) && failVal.equalsIgnoreCase(respVal.toString)
        }
      }

    if(failureReturned) {
      return Some(PollFailure(job, s"pollFailure response returned: $pollFailure"))
    }

    // If the Response contains the pollFor pair, trigger Poll Success
    val pollForReturned =
      pollFor.exists { case (pollKey, pollVal) =>
        response.exists { case (respKey, respVal) =>
          log.debug(s"Comparing $respKey/$respVal to $pollKey/$pollVal")

          pollKey.equalsIgnoreCase(respKey) && pollVal.equalsIgnoreCase(respVal.toString)
        }
      }

    if(pollForReturned) {
      return Some(PollSuccess(job))
    }

    // Neither Success nor Failure condition triggered.
    None
  }

  /**
   * Cancel the Timeout trigger and clean up Actor resources
   */
  private def shutdown(): Unit = {
    timeout.cancel()

    context.stop(self)
  }

}
