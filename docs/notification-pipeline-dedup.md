# Notification pipeline dedup: two ids, two purposes

The notification pipeline carries two distinct identifiers that are easy to conflate because,
for a message's first delivery, they happen to hold the same value:

| Id | Purpose | Scope | Stability |
|---|---|---|---|
| **Source dedup id** (SQS `MessageDeduplicationId`) | Transport-level: tells the source SQS FIFO queue "this is/isn't the same delivery attempt" within its 5-minute dedup window | One SQS queue | Must be **unique per intended delivery** — a replay is a new intended delivery |
| **ASB messageId** | Business-level: the stable key ASB (and any downstream consumer) uses to dedup the same logical event across its whole lifetime, including replays | End-to-end (backend → gateway → ASB) | Must be **stable across replays** of the same logical event |

Both are seeded from the backend outbox's `eventId` (`OutboxPublishService.publishToSns`,
`trade-imports-animals-backend`, EUDPA-261) — the SNS publish sets
`.messageDeduplicationId(event.getEventId())` **and**, since EUDPA-261, the message body carries
`eventId` too (`OutboxPublishedMessage`). For a message's original, first-time delivery these two
ids are identical, which is exactly why it's tempting (and was, before this fix, actually done) to
treat them as one value.

## Where they diverge: replay

`DlqService.replayBatch` re-sends a DLQ message to the source queue. Reusing the message's
`eventId` as the re-send's `MessageDeduplicationId` seems natural — but the source queue's FIFO
dedup window is keyed on exactly that value, for 5 minutes from the *original* send. A message
that fails fast can reach the DLQ in well under 5 minutes (`maxReceiveCount` × the visibility
timeout), so an operator replaying promptly would have the resend **silently deduped by SQS**:
`sendMessageBatch` reports the entry as successful, but SQS never actually delivers it. The old
code then deleted the "replayed" message from the DLQ anyway — a silent, unrecoverable loss with
no error surfaced anywhere (`DlqService.replayBatch`, pre-fix).

## The fix

- **`DlqService.replayDedupId(Message)`** mints a fresh, unique source dedup id for every replay
  send (`idOf(message) + ":replay:" + UUID.randomUUID()`), so the source queue's dedup window can
  never suppress a deliberate re-drive. The `idOf(message)` prefix keeps the transport id
  traceable back to the operator-facing selection id in SQS-level logs/traces.
- **`NotificationSqsListener.receive`** derives the ASB messageId from the message **body**
  `eventId` (`EventEnvelope.eventId`), falling back to the SQS dedup header only when the body
  has none. This is what keeps the ASB-level dedup key stable across a replay even though the
  transport dedup id is now different on each attempt — the source-queue header differs, but the
  body (and therefore the ASB messageId) doesn't.

## Known limitation: double-replay

Because the transport dedup id is now unique per send, SQS-level dedup no longer accidentally
protects against an operator (or the admin UI) submitting the *same* replay request twice in
quick succession — e.g. a genuine double-click. That will now produce two source-queue
deliveries, each independently forwarded to ASB with the *same* ASB messageId (the stable
`eventId`). Cross-pipeline dedup for that scenario relies on ASB's own duplicate detection keyed
on messageId, which is **not currently enabled** anywhere in this pipeline (see
`QueueMessageSender`'s messageId Javadoc: "if it is ever enabled").

This is a deliberate, documented trade-off for EUDPA-253, not an oversight — the alternative
(reusing the same id) is what caused the silent-loss bug this doc exists to explain. Enabling ASB
duplicate detection, and any admin-side double-click mitigation, are tracked separately and out
of scope here.
