################################################################################
# Stage 1: Build
# - Full JDK for compilation
# - Create executable JAR
################################################################################
FROM amazoncorretto:25-alpine AS build

WORKDIR /build

RUN apk add --no-cache maven

COPY pom.xml .

RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests -B

################################################################################
# Stage 2: Development (run pre-built JAR)
# - For local development with docker compose
################################################################################
FROM amazoncorretto:25-alpine AS development

WORKDIR /app

RUN apk add --no-cache curl bash

COPY --from=build /build/target/*.jar app.jar

USER nobody

EXPOSE 8088

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:8088/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

################################################################################
# Stage 3: Dev-run (Maven source mount)
# - Maven present; no pre-built JAR
# - Mount src/ from host for live recompile via `docker compose restart`
################################################################################
FROM amazoncorretto:25-alpine AS dev-run

WORKDIR /app

RUN apk add --no-cache maven curl bash

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src

EXPOSE 8088

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 \
  CMD curl -f http://localhost:8088/health || exit 1

CMD ["mvn", "spring-boot:run", "-Dspring-boot.run.profiles=local"]

################################################################################
# Stage 4: Production
# - Minimal runtime image
# - Meets all CDP platform requirements
################################################################################
FROM amazoncorretto:25-alpine AS production

WORKDIR /app

# CDP PLATFORM REQUIREMENTS:
# - curl: Required for ECS healthcheck
# - shell: Required for CMD-SHELL healthcheck
RUN apk add --no-cache curl

COPY --from=build /build/target/*.jar app.jar

USER nobody

EXPOSE 8088

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:8088/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
