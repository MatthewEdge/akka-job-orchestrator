package edge.labs.shopping

import java.time.LocalDateTime

/*
    "transId": "ABC123",
    "transDate": "YYYY-MM-DD HH:MM:SS.s",
    "transStoreId": "ABC123"
 */
case class Transaction(transId: String, transDate: LocalDateTime, transStoreId: String)