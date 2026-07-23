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
| `NOTIFICATION_SQS_QUEUE_URL` | Base SQS queue URL, **without** a `.fifo`/`-deadletter.fifo` suffix — the app appends `.fifo` for the source queue and `-deadletter.fifo` for the DLQ (used by the [DLQ API](#dlq-api)), since both are the same base queue name with a different suffix |
| `NOTIFICATION_SQS_ARN` | Base SQS queue ARN, same base name/suffix convention as `NOTIFICATION_SQS_QUEUE_URL` — the app appends `-deadletter.fifo` for the DLQ's ARN, needed by `replay-all`'s `StartMessageMoveTask` (which takes an ARN, not a URL) |
| `AWS_REGION` | AWS region (default: `eu-west-2`) |
| `APP_AWS_ENDPOINT_OVERRIDE` | Floci endpoint for local development (leave unset in deployed environments) |
| `SQS_WAIT_TIME_SECONDS` | SQS long-poll wait time (default: `10`, awspring's own default) |
| `SQS_MAX_MESSAGES` | Maximum concurrent SQS messages (default: `10`) |
| `TRADE_IMPORTS_ANIMALS_ADMIN_SECRET` | Shared secret guarding the [DLQ API](#dlq-api)'s replay-all/delete-all operations (`Trade-Imports-Animals-Admin-Secret` header). The same value the admin app holds; must match across both services per environment |

There is no in-process retry: a transient ASB publish failure propagates on the first attempt, so SQS's own redelivery (governed by the queue's visibility timeout and `maxReceiveCount`, both CDP platform defaults) handles retries and eventual DLQ routing.

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
| Transient ASB failure (timeout, throttle) | Message left in SQS for native redelivery → DLQ after max receive count (no in-process retry) |
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

A REST API over the notification dead-letter queue (derived from `NOTIFICATION_SQS_QUEUE_URL`, see
above) for operators. JSON is snake_case.

| Method & path | Purpose |
|---|---|
| `GET /dlq/notifications?limit={n}` | List up to `n` DLQ messages from the front, plus the queue's approximate depth (`limit` defaults to 10 and is capped at 25 — a value above 25 returns `400 Bad Request`; assembled from successive ≤10-message SQS receives until `n` or the queue is exhausted) |
| `POST /dlq/notifications/replay-all` | Move every DLQ message back onto the source queue via native SQS `StartMessageMoveTask` (no destination specified — defaults to the source queue via the DLQ's redrive-allow-policy). Requires the `Trade-Imports-Animals-Admin-Secret` header |
| `POST /dlq/notifications/delete-all` | Wipe the DLQ via native SQS `PurgeQueue`. Requires the `Trade-Imports-Animals-Admin-Secret` header |

The list is read-only and open (no auth); replay-all/delete-all are guarded by the shared-secret
header.

There is no per-message replay or delete — only the two bulk actions above. A poison-pill message
can't be individually removed: it either keeps cycling back into the DLQ via `replay-all`, or is
cleared (along with everything else) via `delete-all`. Both operations are **asynchronous** and allow
only one in-flight task per queue — a second call while one is running fails with `502 Bad Gateway`
(`AWS.SimpleQueueService.MessageMoveTask.LimitExceeded` for replay-all; SQS's 60-second purge cooldown
for delete-all). `delete-all` in particular can take up to 60 seconds to fully complete per the
[AWS API reference](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_PurgeQueue.html) —
a `list` called immediately after may still show messages already slated for removal.

The `id` is the message's `eventId` from the enveloped body when present, otherwise its SQS
`MessageDeduplicationId`. Because SQS has no stable cursor and `GetQueueAttributes` counts are
eventually consistent, a list is a **best-effort snapshot** — the set and `approximate_count` can
drift between calls.

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

Integration tests use [Testcontainers](https://testcontainers.com/) to spin up a real Azure Service Bus emulator (backed by SQL Server) and Floci (SQS) to verify end-to-end message delivery across both ingress paths. Docker must be running.

### Running

Run the application with the `local` Spring profile, which enables the Floci endpoint override and additional actuator endpoints for debugging:

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
