# trade-imports-dynamics-gateway

Centralised gateway forwarding notification events to Azure Service Bus (ASB). Events arrive via two paths: an SQS FIFO queue (primary production pipeline) and an HTTP endpoint (secondary/diagnostic path).

* [Docker Compose](#docker-compose)
* [Configuration](#configuration)
* [Notification pipeline (SQS)](#notification-pipeline-sqs)
* [Endpoint](#endpoint)
* [Testing](#testing)
* [Running](#running)
* [SonarCloud](#sonarcloud)
* [Dependabot](#dependabot)


### Docker Compose

A Docker Compose template is in [compose.yml](compose.yml).

A local environment with:

- LocalStack for AWS services (SQS, SNS, S3, STS, CloudWatch)
- Azure Service Bus emulator (backed by SQL Server)
- This service.

```bash
docker compose --profile services up --build -d
```

Start just infrastructure (emulator + Localstack, no service):

```bash
docker compose --profile infra up -d
```

The gateway connects to the local Service Bus emulator by default using `local-queue`. To target TST instead, export the SAS connection string (with `EntityPath`) in your shell before running compose — it will override the emulator default:

```bash
export AZURE_SERVICE_BUS_CONNECTION_STRING="Endpoint=sb://...;SharedAccessKeyName=...;SharedAccessKey=...;EntityPath=..."
docker compose --profile services up --build -d
```

A more extensive setup is available in [github.com/DEFRA/cdp-local-environment](https://github.com/DEFRA/cdp-local-environment)

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

The listener consumes messages from the SQS FIFO queue, validates the body is valid JSON, and forwards it to Azure Service Bus. The SQS `MessageGroupId` is used as the ASB `sessionId`. Each forwarded message is assigned a UUID `messageId` and `contentType` of `application/json`.

Error handling:

| Scenario | Behaviour |
|---|---|
| Transient ASB failure (timeout, throttle) | Message left in SQS for retry → DLQ after max receive count |
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

Run the application with the `local` Spring profile (after starting infrastructure):

```bash
docker compose --profile infra up -d
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Or equivalently:

```bash
docker compose --profile infra up -d
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
