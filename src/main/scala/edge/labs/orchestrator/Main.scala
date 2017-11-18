package edge.labs.orchestrator

import akka.actor.Props
import akka.event.slf4j.Logger
import akka.http.scaladsl._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import edge.labs.orchestrator.events.EventReader.{FetchEvents, ReceiveEvents}
import edge.labs.orchestrator.events.repository.PersistentInMemEventRepository
import edge.labs.orchestrator.events.{EventReader, LogFileEventWriter}
import edge.labs.orchestrator.util.json.JsonSupport

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Handles the creation of required root Actors such as the PipelineRepositoryActor,
 * Supervisor (if starting from the command line, etc)
 *
 * @author medge
 */
object Main extends App with JsonSupport {

  import Akka._

  val log = Logger("Main")

  val settings = Settings()

  // Spin up the Event Reader/Writer with the desired EventRepository implementation
  val repo = PersistentInMemEventRepository(
    (key: String, events: Set[Event]) => { /* no-op */ }
  )
  val eventReader = createRootActor(EventReader.props(repo), "EventReader")
  val eventWriter = createRootActor(Props(new LogFileEventWriter(repo)), "EventWriter")

  // Spin up the Reaper
  val reaper = createRootActor(Props(new ProdReaper), "Reaper")

  //////////////////////////////////////////////////////////////////////////////////////////
  //                            REST API
  //////////////////////////////////////////////////////////////////////////////////////////

  // Moves any Future{} work to a separate dispatcher so that the default Actor dispatcher is not interferred with
  // see http://doc.akka.io/docs/akka/2.4.8/scala/http/handling-blocking-operations-in-akka-http-routes.html
  implicit val futureDispatcher = Akka.getDispatcher(settings.blockingIoDispatcher)

  val route: Route =
    pathPrefix("orchestrator" / "v1") {
      // GET /orchestrator/v1/run
      // Run Orchestrator with the default Job definitions and a generated runDate
      path("run") {
        val runDate = Runner.generateRunDate()

        log.info(s"Attempting to run Orchestrator with $runDate runDate")

        get {
          runAsync(runDate) {
            Runner.run(runDate)
          }
        }
      } ~
      // GET /orchestrator/v1/run/{runDate}
      // Run Orchestrator with the default Job definitions with an overridden runDate
      path("run" / Segment) { runDate =>
        get {
          log.info(s"Attempting to run Orchestrator with $runDate runDate")

          complete {
            jsonResponse(
              Runner.run(runDate)
            )
          }
        }
      } ~
      // GET /orchestrator/v1/status/{runDate}
      // Get the status of a Orchestrator run for the given runDate
      path("status" / Segment) { runDate =>
        get {
          val evtsFuture = (eventReader ? FetchEvents(runDate)).mapTo[ReceiveEvents]

          // Generate a JSON response according to the results of evtsFuture
          onComplete(evtsFuture) {
            case Success(evtsMessage) =>
              complete {
                jsonResponse(
                  Status(evtsMessage.runDate, evtsMessage.events.toArray)
                )
              }

            case Failure(ex) =>
              complete {
                log.error(s"Failed to fetch events for $runDate", ex)

                jsonResponse(
                  Status(runDate, "Failed to fetch Status events for the given runDate")
                )
              }
          }
        }
      }
    }

  // Bind to port defined in Settings on 0.0.0.0 to respond to REST requests
  // Note: 0.0.0.0 is REQUIRED to work on EC2 instances
  log.info(s"Starting REST API on port ${settings.port}")

  Http().bindAndHandle(route, "0.0.0.0", settings.port)

  /**
   * Abstraction of running a Akka HTTP route asynchronously using the given Future. Converts the response
   * to JSON using jsonResponse()
   */
  def runAsync[T <: AnyRef](runDate: String)(future: ⇒ Future[T])(implicit mfT: Manifest[T]) = {
    onComplete(future) {
      case Success(resp) => complete(jsonResponse(resp))
      case Failure(ex) => complete(jsonResponse(Status(runDate, "Request failed to execute")))
    }
  }

}
