package edge.labs.shopping

import java.time.LocalDateTime

case class Transaction(
  transId: String,
  transDate: LocalDateTime,
  transStoreId: String)