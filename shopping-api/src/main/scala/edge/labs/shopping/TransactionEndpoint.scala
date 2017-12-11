package edge.labs.shopping

import java.time.LocalDateTime
import java.util.UUID

import io.finch._

/**
 * @author medge
 */
object TransactionEndpoint {

  def apply(): Endpoint[Transaction] = {
    get("ids") {
      Ok(
        Transaction(UUID.randomUUID().toString, LocalDateTime.now(), "A12") // TODO DB fetch
      )
    }
  }
}
