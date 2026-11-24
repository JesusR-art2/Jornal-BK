# syntax=docker/dockerfile:1
# Una sola imagen de construcción para todos los módulos; MODULE elige qué jar queda en la imagen final.
#   docker build --build-arg MODULE=jornal-core -t jornal/core .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY jornal-common/pom.xml jornal-common/
COPY jornal-core/pom.xml jornal-core/
COPY jornal-payments/pom.xml jornal-payments/
COPY jornal-dispatch/pom.xml jornal-dispatch/
COPY jornal-notifications/pom.xml jornal-notifications/
COPY jornal-billing/pom.xml jornal-billing/
COPY jornal-common/src jornal-common/src
COPY jornal-core/src jornal-core/src
COPY jornal-payments/src jornal-payments/src
COPY jornal-dispatch/src jornal-dispatch/src
COPY jornal-notifications/src jornal-notifications/src
COPY jornal-billing/src jornal-billing/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 1001 jornal
WORKDIR /app
ARG MODULE
COPY --from=build /src/${MODULE}/target/${MODULE}-0.0.1-SNAPSHOT.jar app.jar
USER jornal
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:+UseSerialGC -Dfile.encoding=UTF-8"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
