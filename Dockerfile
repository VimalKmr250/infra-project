# syntax=docker/dockerfile:1

# Built for both linux/amd64 (Cloud Run) and linux/arm64 (Raspberry Pi 4/5).

# ---------------------------------------------------------------- build ------
# Pinned to the builder's own architecture via $BUILDPLATFORM. Without it buildx
# would run this whole stage once per target platform, compiling the Angular app
# and the Java sources a second time under arm64 emulation - many minutes of work
# for a byte-identical result, because a Spring Boot jar is architecture-neutral.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Build definitions first: these change far less often than source, so the
# dependency-resolution layers stay cached across ordinary code edits.
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY backend/build.gradle.kts backend/
COPY frontend/build.gradle.kts frontend/
COPY frontend/package.json frontend/package-lock.json frontend/

RUN chmod +x ./gradlew

COPY backend backend
COPY frontend frontend

ARG APP_VERSION=0.0.1-SNAPSHOT
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :backend:bootJar -PappVersion="${APP_VERSION}" \
    && cp backend/build/libs/backend-*.jar /workspace/app.jar

# Split the jar into layers that change at different rates. The Pi pulls over a
# home connection, and this means a code-only change ships a few hundred KB
# rather than the entire dependency set.
RUN java -Djarmode=tools -jar /workspace/app.jar extract --layers --launcher --destination /extracted

# -------------------------------------------------------------- runtime ------
# This stage IS built per target architecture, but it only copies files and adds
# a user, so emulation costs almost nothing.
FROM eclipse-temurin:25-jre AS runtime

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --create-home app

WORKDIR /app

# Ordered least- to most-frequently changed.
COPY --from=build --chown=app:app /extracted/dependencies/ ./
COPY --from=build --chown=app:app /extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /extracted/application/ ./

USER app

# Cloud Run injects PORT; application.yml reads it with a default of 8080.
ENV PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
