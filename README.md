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
