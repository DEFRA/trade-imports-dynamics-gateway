# trade-imports-dynamics-gateway

Centralised gateway forwarding notification events to Azure Service Bus (ASB). Events arrive via two paths: an SQS FIFO queue (primary production pipeline) and an HTTP endpoint (secondary/diagnostic path).

* [Local stack](#local-stack)
* [Configuration](#configuration)
* [Notification pipeline (SQS)](#notification-pipeline-sqs)
* [Endpoint](#endpoint)
* [DLQ API](#dlq-api)
* [API documentation (OpenAPI)](#api-documentation-openapi)
* [Testing](#testing)
* [Running](#running)
* [SonarCloud](#sonarcloud)
* [Dependabot](#dependabot)


### Local stack

The local environment for the trade-imports-animals services is the workspace
stack in
[DEFRA/trade-imports-animals-workspace](https://github.com/DEFRA/trade-imports-animals-workspace):

```bash
# from the workspace root
./scripts/stack/run-stack.sh        # full stack from published images
./scripts/stack/run-stack.sh -e gateway   # everything except this service (run it natively)
./scripts/stack/stop-stack.sh       # tear down and wipe volumes
```

This gateway runs as part of that stack (the `servicebus` profile provides a
local Azure Service Bus emulator for it to talk to). The stack stages this
repo's `servicebus/servicebus-config.json` as the emulator's entity config.
The integration tests, by contrast, are self-contained — they spin up their
own emulator via Testcontainers (`mvn verify`), no running stack required.

### Configuration

The service connects to Azure Service Bus using a SAS send-only connection string and consumes notification events from an SQS FIFO queue. The following environment variables must be set in non-local environments:

| Variable | Description |
|---|---|
| `AZURE_SERVICE_BUS_CONNECTION_STRING` | SAS connection string including `EntityPath` — e.g. `Endpoint=sb://...;SharedAccessKeyName=...;SharedAccessKey=...;EntityPath=<queue>` |
| `NOTIFICATION_SQS_QUEUE_URL` | URL of the SQS FIFO queue for notification events |
| `NOTIFICATION_SQS_DLQ_URL` | URL of the notification dead-letter queue (CDP provisions it with a `-deadletter` suffix), used by the [DLQ API](#dlq-api) |
| `AWS_REGION` | AWS region (default: `eu-west-2`) |
| `APP_AWS_ENDPOINT_OVERRIDE` | LocalStack endpoint for local development (leave unset in deployed environments) |
| `SQS_VISIBILITY_TIMEOUT_SECONDS` | SQS visibility timeout (default: `30`) |
| `SQS_WAIT_TIME_SECONDS` | SQS long-poll wait time (default: `20`) |
| `SQS_MAX_MESSAGES` | Maximum concurrent SQS messages (default: `10`) |
| `SQS_RETRY_MAX_ATTEMPTS` | In-process retry attempts for a transient ASB publish failure, including the first (default: `4`; `1` disables retry) |
| `SQS_RETRY_INITIAL_INTERVAL_MS` | First backoff before retrying, in ms (default: `1000`) |
| `SQS_RETRY_MULTIPLIER` | Growth factor applied to the backoff between attempts (default: `2.0`) |
| `SQS_RETRY_MAX_INTERVAL_MS` | Ceiling for any single backoff, in ms (default: `10000`) |
| `TRADE_IMPORTS_ANIMALS_ADMIN_SECRET` | Shared secret required on the [DLQ API](#dlq-api) replay/delete calls (`Trade-Imports-Animals-Admin-Secret` header). The same value the admin app holds; must match across both services per environment. A blank value rejects all replay/delete calls |

The transient-failure retry happens in-process and holds the message, so the total retry window (the sum of the backoffs across `SQS_RETRY_MAX_ATTEMPTS`) must stay below `SQS_VISIBILITY_TIMEOUT_SECONDS`; otherwise the message reappears on the queue and is processed concurrently. The defaults give `1s + 2s + 4s = 7s` across 4 attempts, well under the 30s visibility timeout. The service validates this invariant at startup and refuses to start if it is violated.

The service validates the ASB connection string at startup and will refuse to start if it is missing or blank. The target queue is taken from the `EntityPath` component of the connection string.

The `local` Spring profile (`application-local.yml`) provides placeholder defaults so the service can start without real Azure or AWS credentials during local development.

### Notification pipeline (SQS)

The primary production flow:

```
SNS (FIFO) → SQS FIFO queue → NotificationSqsListener → QueueMessageSender → ASB session queue
```

The listener consumes messages from the SQS FIFO queue, validates the body is valid JSON, and forwards it to Azure Service Bus. The SQS `MessageGroupId` is used as the ASB `sessionId`. Each forwarded message is assigned a UUID `messageId` and `contentType` of `application/json`.

Error handling:

| Scenario | Behaviour |
|---|---|
| Transient ASB failure (timeout, throttle) | Retried in-process with exponential backoff (`SQS_RETRY_*`); on exhaustion the message is left in SQS for redelivery → DLQ after max receive count |
| Non-transient ASB failure (unauthorized, entity not found) | Message deleted from SQS |
| Invalid JSON body | Message deleted from SQS |
| Missing or blank `MessageGroupId` | Message deleted from SQS |

### Endpoint

`POST /events` — accepts a JSON body and forwards it to Azure Service Bus (secondary/diagnostic path).

The request body must include an `aggregateId` field, which is used as the ASB `sessionId`.

| Response | Condition |
|---|---|
| `202 Accepted` | Message sent successfully |
| `400 Bad Request` | Body is missing, malformed JSON, or `aggregateId` is missing/blank |
| `415 Unsupported Media Type` | Content-Type is not `application/json` |
| `502 Bad Gateway` | Azure Service Bus send failed |

Each message is assigned a UUID `messageId` which is logged on success for correlation.

### DLQ API

A REST API over the notification dead-letter queue (`NOTIFICATION_SQS_DLQ_URL`) for operators to
list, replay and delete individual dead-lettered messages. JSON is snake_case.

| Method & path | Purpose |
|---|---|
| `GET /dlq/notifications?limit={n}` | List up to `n` DLQ messages from the front, plus the queue's approximate depth (`limit` defaults to 10; assembled from successive ≤10-message SQS receives until `n` or the queue is exhausted) |
| `POST /dlq/notifications/replay` | Re-send the selected messages to the source queue, then remove them from the DLQ |
| `DELETE /dlq/notifications` | Delete the selected messages from the DLQ |

The read-only list is open; **replay and delete require the shared-secret header**
`Trade-Imports-Animals-Admin-Secret` matching `TRADE_IMPORTS_ANIMALS_ADMIN_SECRET` — a missing,
blank or mismatched secret is rejected with `401 Unauthorized` before reaching the handler.

Replay and delete take a JSON body of message ids; replay returns `200 OK` and delete returns
`204 No Content`, both with no body, once the batch has been processed:

```jsonc
// request — POST /dlq/notifications/replay  or  DELETE /dlq/notifications
{ "ids": ["<id>", "<id>"] }
```

Ids that are not found on the DLQ (e.g. blocked behind an in-flight FIFO group) are logged and left
on the DLQ rather than reported in the response, so the action can simply be retried for any that
remain.

| Response | Condition |
|---|---|
| `200 OK` | List succeeded, or the replay batch was processed |
| `204 No Content` | The delete batch was processed |
| `400 Bad Request` | `limit` is below 1 or above 200, or a replay/delete body is missing, malformed, or has empty `ids` |
| `401 Unauthorized` | Replay/delete called without a valid `Trade-Imports-Animals-Admin-Secret` header |

The `id` is the message's `eventId` from the enveloped body when present, otherwise its SQS
`MessageDeduplicationId`. Because SQS has no stable cursor and `GetQueueAttributes` counts are
eventually consistent, a list is a **best-effort snapshot** — the set and `approximate_count` can
drift between calls. Replay preserves the FIFO `MessageGroupId` but re-sends with a **fresh, unique**
transport dedup id — not the original `id` — so a prompt replay is never silently swallowed by the
source queue's 5-minute FIFO dedup window; the eventual ASB `messageId` still stays equal to the
original `eventId` regardless (see `docs/notification-pipeline-dedup.md`). Messages are only ever
removed individually, never by purging the queue.

#### Operator runbook — replay & delete

* **Replay only after the underlying bug is fixed.** A replayed message is re-consumed and
  re-published immediately; replaying into a still-broken pipeline just dead-letters it again.
* **Don't replay the same message twice in quick succession.** Unlike list/delete, replay is not
  idempotent at the transport level — a double-click (or repeated retry of a failed replay request)
  can produce two separate deliveries to the source queue, each independently forwarded to ASB. If a
  replay's outcome is unclear, check the source queue / ASB rather than replaying again.
* **Receive count resets on replay.** The re-sent message starts at receive count 1 on the source
  queue, so it gets the full `maxReceiveCount` budget again.
* **FIFO group-blocking.** Messages in the same `MessageGroupId` are processed in order; a message
  cannot be replayed/listed past an earlier in-flight message in its group until that one clears.
  Drain a group by actioning the messages shown, then listing again.
* **Snapshot, not a guarantee.** `approximate_count` and the listed set can drift between calls; treat
  the list as best-effort.
* **Delete is irreversible** for the selected ids and leaves all other messages untouched.

### API documentation (OpenAPI)

The REST endpoints are documented with OpenAPI via [springdoc](https://springdoc.org/). The generated
spec is served at `/v3/api-docs`. The Swagger UI is **enabled only in the `local` profile**
(`/swagger-ui.html`) and disabled in deployed environments (`springdoc.swagger-ui.enabled=false`).

### Testing

Run unit tests only:

```bash
mvn test
```

Run the full build including integration tests:

```bash
mvn verify
```

Integration tests use [Testcontainers](https://testcontainers.com/) to spin up a real Azure Service Bus emulator (backed by SQL Server) and LocalStack (SQS) to verify end-to-end message delivery across both ingress paths. Docker must be running.

### Running

Run the application with the `local` Spring profile, which enables LocalStack endpoint override and additional actuator endpoints for debugging:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Or equivalently:

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

The `local` profile defaults to plain AMQP against the emulator on `localhost:5672`
(`UseDevelopmentEmulator=true`, queue `local-queue`). Override with
`AZURE_SERVICE_BUS_CONNECTION_STRING` to target TST instead.

### SonarCloud

SonarCloud configuration is available in the GitHub Action workflows.

### Dependabot

A Dependabot configuration file is at [.github/dependabot.yml](.github/dependabot.yml).


### About the licence

The Open Government Licence (OGL) was developed by the Controller of Her Majesty's Stationery Office (HMSO) to enable
information providers in the public sector to license the use and re-use of their information under a common open
licence.

It is designed to encourage use and re-use of information freely and flexibly, with only a few conditions.
