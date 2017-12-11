# Shopping Transactions API

Migrates transaction records from source to target data store with a couple transformations (deduplication, enrichment, etc)
performed in transit.

## Transaction

```json
{
    "transId": "ABC123",
    "transDate": "YYYY-MM-DD HH:MM:SS.s",
    "transStoreId": "ABC123"
}
```