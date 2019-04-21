package edge.labs.orchestrator

import java.io.File

import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Necessary traits for ScalaTest and helper methods for unit testing
 *
 * Created by medge on 5/31/16.
 */
trait BaseTest extends FlatSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll {

  def testResource(relPath: String): String = {
    new File(s"src/test/resources/$relPath").getAbsolutePath
  }

  /*******************************************
   *           Future Helpers
   *******************************************/
  implicit val futureEc: ExecutionContext = ExecutionContext.global

  /**
   * Execute a Future and wait for the result. By default it waits a maximum of 5 seconds
   *
   * @param f Future[T]
   * @param maxWait FiniteDuration default 5.seconds
   * @tparam T Future return type
   * @return T result of the Future
   */
  def awaitFuture[T](f: Future[T], maxWait: FiniteDuration = 5.seconds): T = {
    Await.result(f, maxWait)
  }

}
