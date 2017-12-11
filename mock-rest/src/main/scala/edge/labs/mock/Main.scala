package edge.labs.mock

import com.twitter.finagle.Http
import com.twitter.util.Await
import org.slf4j.LoggerFactory

import io.circe.generic.auto._
import io.finch.circe._

object Main extends App {

  val log = LoggerFactory.getLogger("MockServer")

  val services = EchoEndpoint()

  Await.ready(
    Http.server.serve(":8080", services.toService)
  )
}