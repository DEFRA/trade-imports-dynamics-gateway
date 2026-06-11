# trade-imports-dynamics-gateway

Centralised gateway forwarding events to Azure Service Bus.

* [Docker Compose](#docker-compose)
* [Testing](#testing)
* [Running](#running)
* [SonarCloud](#sonarcloud)
* [Dependabot](#dependabot)


### Docker Compose

A Docker Compose template is in [compose.yml](compose.yml).

A local environment with:

- Localstack for AWS services (S3, STS, CloudWatch)
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

The service connects to Azure Service Bus using a SAS send-only connection string. The following environment variables must be set in non-local environments:

| Variable | Description |
|---|---|
| `AZURE_SERVICE_BUS_CONNECTION_STRING` | SAS connection string including `EntityPath` — e.g. `Endpoint=sb://...;SharedAccessKeyName=...;SharedAccessKey=...;EntityPath=<queue>` |

The service validates the connection string at startup and will refuse to start if it is missing or blank. The target queue is taken from the `EntityPath` component of the connection string.

The `local` Spring profile (`application-local.yml`) provides placeholder defaults so the service can start without real Azure credentials during local development.

### Endpoint

`POST /events` — accepts a JSON body and forwards it to Azure Service Bus.

| Response | Condition |
|---|---|
| `202 Accepted` | Message sent successfully |
| `400 Bad Request` | Body is missing or malformed JSON |
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

Integration tests use [Testcontainers](https://testcontainers.com/) to spin up a real Azure Service Bus emulator (backed by SQL Server) and verify end-to-end message delivery. Docker must be running.

### Running

Run the application with the `local` Spring profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Or equivalently:

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

### SonarCloud

SonarCloud configuration is available in the GitHub Action workflows.

### Dependabot

A Dependabot configuration file is at [.github/dependabot.yml](.github/dependabot.yml).


### About the licence

The Open Government Licence (OGL) was developed by the Controller of Her Majesty's Stationery Office (HMSO) to enable
information providers in the public sector to license the use and re-use of their information under a common open
licence.

It is designed to encourage use and re-use of information freely and flexibly, with only a few conditions.
