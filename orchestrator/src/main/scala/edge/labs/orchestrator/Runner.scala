package edge.labs.orchestrator

import java.time.LocalDate

import akka.actor.{ActorContext, ActorRef, Props}
import akka.event.slf4j.Logger
import edge.labs.orchestrator.Reaper.Watch
import edge.labs.orchestrator.Supervisor.Start
import edge.labs.orchestrator.dag.DAG
import edge.labs.orchestrator.jobs.Job
import edge.labs.orchestrator.jobs.rest.RestApiActor
import edge.labs.orchestrator.pipelines.repository.PipelineRepository
import edge.labs.orchestrator.pipelines.{Pipeline, PipelineSupervisor}
import edge.labs.orchestrator.util.rest.ScalajRestClient

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * @author medge
 */
object Runner {

  private val log = Logger("Runner")
  val settings = Settings()

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
  def run(runDate: String)(implicit ec: ExecutionContext) = Future[Status] {
    val dag =
      Await.result(
        fetchPipelineDagFrom(settings.pipelineDefaultFolder, runDate),
        10.seconds
      )

    log.info("Running with fetched DAG")

    runWith(runDate, dag)
  }

  /**
   * Fetch a Pipeline DAG from the given folder. The basePath of the folder is pulled from Settings
   *
   * @param folderName String
   * @param runDate String
   * @return Future[ DAG[Pipeline] ]
   */
  def fetchPipelineDagFrom(folderName: String, runDate: String)(implicit ec: ExecutionContext): Future[DAG[Pipeline]] = {
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

    // Spin up the Reaper
    val reaper = Akka.createRootActor(Props(new ProdReaper), s"Reaper-$runDate")

    // Have the Reaper watch the Runner for failure
    reaper ! Watch(runner)

    runner

  }

}
