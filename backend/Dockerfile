# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /project
COPY pom.xml ./
COPY api-contract/pom.xml api-contract/pom.xml
COPY backend/pom.xml backend/pom.xml
COPY desktop/pom.xml desktop/pom.xml
COPY api-contract/src api-contract/src
COPY backend/src backend/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl backend -am package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system --gid 10001 library && useradd --system --uid 10001 --gid library --home-dir /app library
WORKDIR /app
COPY --from=build --chown=10001:10001 /project/backend/target/backend-1.0-SNAPSHOT.jar /app/backend.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 CMD curl --fail --silent --max-time 3 http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
