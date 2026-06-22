# trade-imports-dynamics-gateway

Centralised gateway forwarding notification events to Azure Service Bus (ASB). Events arrive via two paths: an SQS FIFO queue (primary production pipeline) and an HTTP endpoint (secondary/diagnostic path).

* [Local stack](#local-stack)
* [Configuration](#configuration)
* [Notification pipeline (SQS)](#notification-pipeline-sqs)
* [Endpoint](#endpoint)
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
| `AWS_REGION` | AWS region (default: `eu-west-2`) |
| `APP_AWS_ENDPOINT_OVERRIDE` | LocalStack endpoint for local development (leave unset in deployed environments) |
| `SQS_VISIBILITY_TIMEOUT_SECONDS` | SQS visibility timeout (default: `30`) |
| `SQS_WAIT_TIME_SECONDS` | SQS long-poll wait time (default: `20`) |
| `SQS_MAX_MESSAGES` | Maximum concurrent SQS messages (default: `10`) |

The service validates the ASB connection string at startup and will refuse to start if it is missing or blank. The target queue is taken from the `EntityPath` component of the connection string.

The `local` Spring profile (`application-local.yml`) provides placeholder defaults so the service can start without real Azure or AWS credentials during local development.

### Notification pipeline (SQS)

The primary production flow:

```
SNS (FIFO) → SQS FIFO queue → NotificationSqsListener → QueueMessageSender → ASB session queue
```

The listener consumes messages from the SQS FIFO queue, validates the message group id and body (non-null, non-blank, valid JSON), and forwards the payload to Azure Service Bus with `contentType` of `application/json`. Two identifiers are carried through:

* The SQS `MessageGroupId` becomes the ASB `sessionId`.
* The inbound SQS `MessageDeduplicationId` (the backend outbox `eventId`) is set as the ASB `messageId`, keeping the deduplication key consistent end-to-end — backend `eventId` → SNS `MessageDeduplicationId` → ASB `messageId`. A fresh UUID is generated only when no deduplication id is present.

A stable `messageId` means ASB duplicate detection can suppress messages that SQS redelivers (at-least-once processing), if the destination queue has it enabled.

Error handling:

| Scenario | Behaviour |
|---|---|
| Transient ASB failure (timeout, throttle) | Message left in SQS for retry → DLQ after max receive count |
| Non-transient ASB failure (unauthorized, entity not found) | Message deleted from SQS |
| Invalid JSON body | Message deleted from SQS |
| Missing or blank message body | Message deleted from SQS |
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

This path has no upstream deduplication id, so a fresh UUID `messageId` is generated and logged on success for correlation. (The SQS pipeline, by contrast, reuses the inbound deduplication id — see above.)

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
