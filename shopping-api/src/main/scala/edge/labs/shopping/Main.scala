package edge.labs.shopping

import com.twitter.finagle.Http
import com.twitter.util.Await
import org.slf4j.LoggerFactory

import io.circe.generic.auto._
import io.finch.circe._

object Main extends App {

  val log = LoggerFactory.getLogger("ShoppingApi")

  val endpoints = TransactionEndpoint()

  Await.ready(
    Http.server.serve(":8080", endpoints.toService)
  )

}
