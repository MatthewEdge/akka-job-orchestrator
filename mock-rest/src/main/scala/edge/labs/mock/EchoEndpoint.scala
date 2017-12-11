package edge.labs.mock

import java.util.UUID

import io.finch._

/**
 * @author medge
 */
object EchoEndpoint {

  case class Echo(id: Option[UUID], msg: String)

  def apply(): Endpoint[String] = {
    get("echo") {
      Ok("Not Implemented")
    }
  }

}
