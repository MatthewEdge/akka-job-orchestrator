package edge.labs.orchestrator.jobs.rest

import java.net.SocketTimeoutException

import akka.actor.{ActorRef, Props}
import akka.pattern.pipe
import edge.labs.orchestrator.Event
import edge.labs.orchestrator.actor.BaseActor
import edge.labs.orchestrator.jobs._
import edge.labs.orchestrator.jobs.rest.PollingRestActor.PollRequest
import edge.labs.orchestrator.rest.{RestClient, RestMethod}

import scala.concurrent.Future


object RestApiActor {

  def props(parent: ActorRef, restClient: RestClient): Props = Props(new RestApiActor(parent, restClient))

  /**
   * Submit a REST Job using the given restClient. Centralizes logic around path construction and REST Client
   * adaptation
   *
   * @param job Job
   * @param restClient RestClient
   * @return (Int, String) httpCode, httpBody
   */
  def submitRestJob(job: Job, restClient: RestClient): (Int, String) = {
    val url = s"${job.baseUrl}/${job.endpoint}/${job.runDate}"
    val httpMethod = job.method.toUpperCase()
    val maybeQueryParams = job.queryParams

    // Submit the REST request
    try {
      httpMethod match {
        case RestMethod.GET =>
          restClient.get(url, maybeQueryParams)

        case RestMethod.POST =>
          // Post requires an additional body param be extracted
          val body = job.body

          restClient.post(url, maybeQueryParams, body)
      }
    } catch {
      case timeout: SocketTimeoutException =>
        timeout.printStackTrace()
        (500, s"REST request timed out for $job")
    }
  }

}

/**
 * Actor for starting a REST service. Does NOT handle return data other
 * than the HTTP status code and a String message, if applicable.
 *
 * Note:
 *    For the sake of simplicity - RestApiActor does NOT extend from WorkerActor right now. This is due to the complexity
 *    of Polling HTTP requests
 *
 * @author medge
 */
class RestApiActor(parent: ActorRef, restClient: RestClient) extends BaseActor {

  import RestApiActor._
  import context.dispatcher

  // Internal message this Actor sends to itself to indicate a standard REST call is done
  sealed case class RestStepCompleted(runDate: String, result: Event, job: Job) extends Event

  override def receive: Receive = {
    case StartJob(job) =>

      // If the Job cannot be processed by this Worker, fail immediately
      val errors = canProcess(job)
      if(errors.nonEmpty) {
        val errMsg = s"Could not start [$job] for the following reasons:\n\n" + errors
        parent ! JobFailed(job, errMsg)

        context.stop(self)
      }
      // Job can be processed
      else {
        // First, notify parent that actual processing has started
        parent ! JobStarted(job)

        startRestJob(job)
      }

    // Actor completed the initial REST step (main REST definition)
    case RestStepCompleted(_, evt, job) =>
      if(job.pollEndpoint.isDefined) {
        startPollingJob(job)
      }
      else {
        finishWith(evt)
      }

    // If the Job was a Polling Job and it succeeded
    case PollingRestActor.PollSuccess(runDate, job) =>
      finishWith(JobCompleted(job))

    case PollingRestActor.PollFailure(runDate, job, reason) =>
      finishWith(JobFailed(job, reason))

    case PollingRestActor.PollTimeout(runDate, job) =>
      finishWith(JobFailed(job, "Polling timeout reached"))

    case m @ _ =>
      log.warning(s"Unrecognized message sent to ${self.path}: $m")
      unhandled(m)
  }

  /**
   * Determine if the implementing Worker Actor can process the given Job
   *
   * @param job Job
   * @return String errors relating to why the Worker Actor CANNOT process the given Job
   */
  def canProcess(job: Job): String = {
    val errs = new StringBuilder

    if(job.endpoint.isEmpty)
      errs.append(s"Job ${job.id} is missing the 'endpoint' key!")

    if(job.method.isEmpty)
      errs.append(s"Job ${job.id} is missing the 'method' key!")

    // Validate a Polling Job
    if(job.pollFor.isDefined && job.pollFor.get.isEmpty)
      errs.append(s"Polling Job ${job.id} did not declare any 'pollFor' (key, value) pairs!")

    errs.toString()
  }

  /**
   * Attempt to run the main REST component of the given Job. Returns the result to itself as a RestStepCompleted using
   * pipeTo.
   *
   * Note: The "main" REST component of a Job is the "endpoint/method/queryParams" step
   *
   * @param job Job
   */
  def startRestJob(job: Job) = {
    log.info(s"Submitting REST request for Job ${job.id}")

    submitJob(job)
      .map(evt => RestStepCompleted(job.runDate, evt, job))
      .pipeTo(self)
  }

  /**
   * Submit the appropriate REST call for the given Job
   *
   * @param job Job
   */
  def submitJob(job: Job): Future[Event] = Future {
    // Submit the REST request
    val (httpCode, httpBody) = submitRestJob(job, restClient)

    parseResponse(job, httpCode, httpBody)
  }

  /**
   * Parse a response from the REST service called and notify the parent of the result
   *
   * @param job Job being executed
   * @param httpCode Http Status Code returned
   * @param httpBody Http Body returned
   */
  def parseResponse(job: Job, httpCode: Int, httpBody: String): Event = {
    // Based on the response, send the appropriate message to the parent
    httpCode match {
      case 200 =>
        JobCompleted(job)
      case 404 | 500 =>
        JobFailed(job, s"Error executing Job:\n\tHttp Code: $httpCode\n\tResponse: $httpBody")
      case _ =>
        JobFailed(job, s"Unknown response returned:\n\tHttp Code: $httpCode\n\tResponse: $httpBody")
    }
  }

  /**
   * Attempt to run the Polling REST component of the given Job. This is delegated to a Child Actor (PollingRestActor)
   * for actual execution
   *
   * @param job Job
   */
  def startPollingJob(job: Job): Unit = {
    val pollingActor = context.actorOf(PollingRestActor.props(self, job, restClient))

    pollingActor ! PollRequest()
  }

  /**
   * Final method to be called which sends the given evt to the parent and shuts down the Actor
   *
   * @param evt Event processing result
   */
  def finishWith(evt: Event): Unit = {
    parent ! evt
    context.stop(self)
  }
}
