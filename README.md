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
- This service.

```bash
docker compose --profile services up --build -d
```

Start just infrastructure:

```bash
docker compose --profile infra up -d
```

A more extensive setup is available in [github.com/DEFRA/cdp-local-environment](https://github.com/DEFRA/cdp-local-environment)

### Testing

Run unit and integration tests with:

```bash
mvn test
```

Or run the full build including integration tests:

```bash
mvn verify
```

Integration tests start a full Spring Boot application context on a random port. No external services or containers are required.

### Running

Run the application with the `local` Spring profile, which enables LocalStack endpoint override and additional actuator endpoints for debugging:

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
