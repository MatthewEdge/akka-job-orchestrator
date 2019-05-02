package edge.labs.orchestrator

import java.time.LocalDate

import akka.actor.{ActorContext, ActorRef, Props}
import akka.event.slf4j.Logger
import akka.http.scaladsl._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import edge.labs.orchestrator.Reaper.Watch
import edge.labs.orchestrator.Supervisor.Start
import edge.labs.orchestrator.dag.DAG
import edge.labs.orchestrator.events.EventReader.{FetchEvents, ReceiveEvents}
import edge.labs.orchestrator.events.repository.PersistentInMemEventRepository
import edge.labs.orchestrator.events.{EventReader, LogFileEventWriter}
import edge.labs.orchestrator.jobs.Job
import edge.labs.orchestrator.jobs.rest.{RestApiActor, ScalajRestClient}
import edge.labs.orchestrator.json.JsonSupport
import edge.labs.orchestrator.pipelines.repository.PipelineRepository
import edge.labs.orchestrator.pipelines.{Pipeline, PipelineSupervisor}

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

  val eventReader = system.actorOf(EventReader.props(repo), "EventReader")
  val eventWriter = system.actorOf(Props(new LogFileEventWriter(repo)), "EventWriter")

  //////////////////////////////////////////////////////////////////////////////////////////
  //                            REST API
  //////////////////////////////////////////////////////////////////////////////////////////

  // Moves any Future{} work to a separate dispatcher so that the default Actor dispatcher is not interferred with
  // see http://doc.akka.io/docs/akka/2.4.8/scala/http/handling-blocking-operations-in-akka-http-routes.html
//  implicit val futureDispatcher = Akka.getDispatcher(settings.blockingIoDispatcher)

  val route: Route =
    pathPrefix("orchestrator" / "v1") {
      // GET /orchestrator/v1/run
      // Run Orchestrator with the default Job definitions and a generated runDate
      path("run") {
        val runDate = generateRunDate()

        log.info(s"Attempting to run Orchestrator with $runDate runDate")

        get {
          runAsync(runDate) {
            run(runDate)
          }
        }
      } ~
      // GET /orchestrator/v1/run/{runDate}
      // Run Orchestrator with the default Job definitions with an overridden runDate
      path("run" / Segment) { runDate =>
        get {
          log.info(s"Attempting to run Orchestrator with $runDate runDate")

          complete {
            jsonResponse(run(runDate))
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

  Http()
    .bindAndHandle(route, "0.0.0.0", settings.port)

  log.info(s"App started on port ${settings.port}")

  /**
   * Abstraction of running a Akka HTTP route asynchronously using the given Future. Converts the response
   * to JSON using jsonResponse()
   */
  def runAsync[T <: AnyRef](runDate: String)(future: ⇒ Future[T])(implicit mfT: Manifest[T]): Route = {
    onComplete(future) {
      case Success(resp) =>
        complete(jsonResponse(resp))
      case Failure(ex) =>
        ex.printStackTrace() // TODO cleaner logging needed
        complete(jsonResponse(Status(runDate, "Request failed to execute")))
    }
  }

  /**
   * @return String yyyymmdd
   */
  def generateRunDate(): String = LocalDate.now().toString.replaceAll("-", "")

  /**
   * Logic to derive runDate and the DAG[Pipeline]
   *
   * @param runDate String runDate attach to the run
   * @return Future[String]
   */
  def run(runDate: String): Future[Status] = Future[Status] {
    val dag = fetchPipelineDagFrom(settings.pipelineDefaultFolder, runDate)
    runWith(runDate, dag)
  }(Akka.getDispatcher(settings.blockingIoDispatcher))

  /**
   * Fetch a Pipeline DAG from the given folder. The basePath of the folder is pulled from Settings
   *
   * @param folderName String
   * @param runDate String
   * @return Future[ DAG[Pipeline] ]
   */
  def fetchPipelineDagFrom(folderName: String, runDate: String): DAG[Pipeline] = {
    val pipelinePath = s"${settings.pipelineJsonBaseUrl}/$folderName"

    log.debug(s"Pipeline Path being used: $pipelinePath")

    PipelineRepository.fetchDAG(pipelinePath, runDate)
  }

  /**
   * Run with the given runDate/DAG
   *
   * @param runDate String
   * @param dag DAG[Pipeline]
   * @return Status
   */
  def runWith(runDate: String, dag: DAG[Pipeline]): Status = {
    val instance = createInstanceFor(dag, runDate)

    instance ! Start()

    Status(runDate, "Running")
  }

  /**
   * Create an instance to run for the given runDate and execute the given DAG[Pipeline]
   *
   * @param dag DAG[Pipeline]
   */
  def createInstanceFor(dag: DAG[Pipeline], runDate: String): ActorRef = {

    // Worker Actor factory method for use by a PipelineSupervisor. Creates RestApiActor instances with a PROD-based
    // RestClient
    val workerActorCreator = (context: ActorContext, job: Job) => {
      context.actorOf(RestApiActor.props(context.self, ScalajRestClient), s"Worker-${job.id}-$runDate")
    }

    // PipelineSupervisor factory method for use by RunSupervisor. Names the created Actor after the Pipeline being executed
    // for debugging purposes
    val pipelineSupervisorCreator = (context: ActorContext, pipeline: Pipeline) => {
      context.actorOf(PipelineSupervisor.props(context.self, pipeline, workerActorCreator), s"PipelineSupervisor-${pipeline.id}-$runDate")
    }

    // Create a single, named instance of the RunSupervisor
    val runner = Akka.createRootActor(
      Supervisor.props(pipelineSupervisorCreator, dag, runDate),
      s"Runner-$runDate"
    )

    // Spin up the Reaper for the run
    val reaper = Akka.createRootActor(Props(new ProdReaper), s"Reaper-$runDate")

    // Have the Reaper watch the Runner for failure
    reaper ! Watch(runner)

    runner

  }

}
