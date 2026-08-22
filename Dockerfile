# syntax=docker/dockerfile:1

# One image, three stages: build the app, build the jar with the app inside it, ship the jar.
#
# The two halves stay independent builds everywhere else — this is the only place they are
# glued together, and the glue is a directory copy. Serving the bundle from Spring keeps the
# browser same-origin in a container exactly as the dev proxy does on a laptop, so there is no
# CORS configuration in the deployed artefact either.

# --- the app ----------------------------------------------------------------------------------
FROM node:24-alpine AS web
WORKDIR /web
# Manifests first: dependencies only reinstall when they actually change.
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

# --- the jar ----------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS api
WORKDIR /src
COPY api/gradlew ./
COPY api/gradle ./gradle
# Fetches the Gradle distribution on its own layer, which is the slow half of a cold build.
RUN ./gradlew --no-daemon --version > /dev/null
COPY api/settings.gradle.kts api/build.gradle.kts ./
COPY api/src ./src
COPY --from=web /web/dist ./src/main/resources/static
# Tests run in CI and on a developer machine; a release build should not need a network for them.
RUN ./gradlew --no-daemon bootJar -x test

# --- what actually ships ------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S dragons && adduser -S dragons -G dragons
WORKDIR /app
COPY --from=api /src/build/libs/dragons-api-*.jar app.jar
USER dragons
EXPOSE 8081
# The container reports its own readiness, so `docker compose up` can be believed.
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
  CMD wget -qO- http://localhost:8081/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
