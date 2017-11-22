package edge.labs.mock

import java.util.Locale

import com.twitter.finagle.Http
import com.twitter.util.Await
import io.circe.generic.auto._
import io.finch._
import io.finch.circe._
import org.slf4j.LoggerFactory

object Main extends App {

  val log = LoggerFactory.getLogger("MockServer")

  case class Time(time: String)

  def currentTime(): String =
    java.util.Calendar.getInstance(Locale.ENGLISH).getTime.toString

  val time: Endpoint[Time] =
    get("time") {
      Ok(Time(currentTime()))
    }

  Await.ready(
    Http.server.serve(":8081", time.toService)
  )
}