# trade-imports-dynamics-gateway

Centralised gateway forwarding events to Azure Service Bus.

* [Local stack](#local-stack)
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
./scripts/stack/stop-stack.sh       # tear down and wipe volumes
```

This gateway is not part of that stack — it talks to Azure Service Bus, and
its integration tests are self-contained (`mvn verify`).

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
