# Dragons of Mugloar

A playable web app for the [Dragons of Mugloar](https://dragonsofmugloar.com/) adventure, with a
built-in solver that can play the game on its own.

> **Status: scaffold.** The workspaces build, test and run; the game itself is not implemented yet.

## Architecture

```
web (Vue 3 + TS + Pinia)  ──►  api (Spring Boot)  ──►  dragonsofmugloar.com/api/v2
```

The browser never calls the game API directly. Ad scoring and message decoding live in one place on
the server, so the human player and the solver rank ads identically, and upstream retries, timeouts
and error mapping have a single home.

| Path | What it is |
|---|---|
| `api/` | Spring Boot 4.1 on Java 21, Gradle (Kotlin DSL) |
| `web/` | Vue 3 + TypeScript + Pinia + Tailwind v4, built with Vite |
| `docs/api-findings.md` | Verified upstream API behaviour, measured rather than assumed |

## Prerequisites

Java 21 and Node 24. Gradle is not required — use the committed wrapper.

## Running it

Two terminals. Start the backend first; the frontend proxies `/api` to it.

```bash
cd api && ./gradlew bootRun
```

```bash
cd web && npm install && npm run dev
```

- App: <http://localhost:5173>
- API docs (Swagger UI): <http://localhost:8081/swagger-ui>
- Health: <http://localhost:8081/actuator/health>

The API listens on **8081** by default; override with `PORT`. The dev proxy target can be pointed
elsewhere with `API_URL`.

## Tests

```bash
cd api && ./gradlew test
```

```bash
cd web && npm run test:run
```

Coverage reports: `./gradlew jacocoTestReport` (`api/build/reports/jacoco`) and
`npm run test:coverage`. Both suites run in CI on every push.
