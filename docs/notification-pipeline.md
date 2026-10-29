# Notification pipeline: design notes

Why the SQS-to-ASB pipeline behaves as it does. The README covers configuration and the API.

## Message ids

- The ASB `MessageId` is the body's `eventId`. Without one it is the SQS `MessageDeduplicationId`, and without either a fresh UUID. The DLQ API shows the same id.
- The body's `eventId` wins over the SQS header because a DLQ replay can carry a different SQS deduplication id from the original delivery, and the ASB id must stay the same across retries and replays.
- The id is never taken from the aggregate id. Every event of one aggregate shares that id, so distinct events would look like duplicates.
- ASB duplicate detection (`RequiresDuplicateDetection`) is off on the queue, so today the id serves as a stable trace id. A stable id means duplicate detection would work unchanged if it were switched on.

## PIMS versions

- Each external event is published three times, as PIMS v0.1.0, v0.2.0 and v0.3.0, so PIMS can move to a newer version when it is ready. The v0.2.0 and v0.3.0 copies' ASB `MessageId` is the same id with `-v2` or `-v3` appended. All three go to the same session, in that order.
- v0.1.0 is published first. A v0.2.0 or v0.3.0 mapping or publish failure is logged with the schema version, counted on its own counter (`notification.sqs.messages`, `outcome=v2-mapping-failed` or `outcome=v3-mapping-failed`) and never fails or retries the SQS message, because a redelivery would send v0.1.0 a second time. One failing does not stop the other.
- Each version stamps its own `schemaVersion` and `schemaUri` instead of passing on the incoming event's values. PIMS tells the streams apart by these values.
- Each version has its own records (`pims/gbnag`, `pims/gbnagv2`, `pims/gbnagv3`), event (`PimsEventV1`, `PimsEventV2`, `PimsEventV3`) and mappers (`Pims*Mapper`, `Pims*MapperV2`, `Pims*MapperV3`), even where a newer version only adds fields to an older one. A change for one version never touches another, and when PIMS has moved off a version, that version is retired by deleting its package, its mappers, its `PimsPayloadMapper` method and its line in `NotificationSqsListener`.
- The envelope records (actor, status change, metadata) are the same in every version, so `PimsEnvelopeMapper` maps them once. `PimsCommonMapperV2` and `PimsCommonMapperV3` do the same for the records that several mappers of their version share. Neither has dependencies, so every mapper can inject them without a bean cycle. `PimsMapperSupport` holds static helpers with no domain meaning.

## Fields

- The v0.1.0 records leave out every field the backend outbox never fills. `finalDestinationLocation` is left out because the v0.1.0 schema has no such field.
- In v0.1.0, `applicableClassification` on a trade line item is a single object: the backend sends exactly one, and the mapper takes the first.
- In v0.2.0 and v0.3.0, a trade line item has no `typeCode` or `urlId`: they are resolved downstream from CN-code reference data. In v0.2.0 an exchanged document has no `issuer`, a referenced document carries only type code, identifier and issue date, and the origin country has no `regionOfOriginCode`. v0.3.0 adds all three: the issuer is the consignment contact, the referenced document's `urlId` names the document-type list, and the region of origin code is passed through. See `schemas/profiles/imports/gb/pims/gbn-ag-pims-v0.2.0-changes.md` and `gbn-ag-pims-v0.3.0-changes.md` in `trade-imports-schemas`.
- Where the schema requires a list to have at least one item (`minItems: 1`), the record component uses `@JsonInclude(NON_EMPTY)`, so an empty list is left out rather than sent as `[]`. Mappers always return an empty list, never null.

## Errors and retries

- `QueueMessageSender` classifies a failed send. A transient `ServiceBusException` or a disposed sender is retryable; anything else is not.
- Spring Cloud AWS wraps listener failures, so `NotificationErrorHandler` walks the cause chain to find the classification.
- Retryable: the handler rethrows, the message is not acknowledged, and SQS redelivers it after the visibility timeout. After `maxReceiveCount` receives SQS moves it to the DLQ.
- Not retryable: the handler returns normally, so the message is acknowledged and deleted.
- The listener logs the SQS receive count on every receipt, so a failing message's progress towards the DLQ is visible. SQS moves it to the DLQ itself, so the gateway writes no log at that moment.

## DLQ browsing

- A list samples the queue depth before receiving. Receiving hides messages, and `ApproximateNumberOfMessages` counts only visible ones, so a later reading would be short by the page just received.
- Every browsed message is made visible again straight away, so browsing never holds a message back from replay.
- SQS calls wait on `join()`, which wraps a failure in `CompletionException`. `GlobalExceptionHandler` unwraps it, so an SQS failure still returns 502 rather than 500.
- Replay-all leaves the destination unset, so SQS moves messages back to the source queue named in the DLQ's redrive-allow policy. CDP provisions the queues that way.
