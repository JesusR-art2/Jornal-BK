# syntax=docker/dockerfile:1
# Imagen de jornal-payments (multietapa). El contexto de construcción es la raíz del repositorio:
#   docker build -f jornal-payments/Dockerfile -t jornal/payments .
# En Render: Dockerfile Path = jornal-payments/Dockerfile, Docker Build Context Directory = . (raíz)

# ---- 1. Construcción: solo este módulo y sus dependencias (jornal-common)
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
COPY jornal-payments/src jornal-payments/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl jornal-payments -am package -DskipTests

# ---- 2. Ejecución: JRE, usuario sin privilegios y salud en /actuator/health
FROM eclipse-temurin:21-jre AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 1001 jornal
WORKDIR /app
COPY --from=build /src/jornal-payments/target/jornal-payments-0.0.1-SNAPSHOT.jar app.jar
USER jornal
# Render asigna el puerto en PORT; en Docker local se usa 8080.
ENV PORT=8080
EXPOSE 8080
# Cabe en 512 MB (plan gratuito de Render). Se puede cambiar con la variable JAVA_TOOL_OPTIONS.
ENV JAVA_TOOL_OPTIONS="-Xmx256m -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8"
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10   CMD curl -fs "http://localhost:${PORT}/actuator/health" || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
