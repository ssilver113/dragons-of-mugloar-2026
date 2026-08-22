# Dragons of Mugloar

A playable web app for the [Dragons of Mugloar](https://dragonsofmugloar.com/) adventure, with a
built-in solver that can play the game on its own.

> **Status: playable, and the solver clears the bar.** Across 46 benchmarked games the solver's
> lowest score was 2700 and its median 4329 — see [Solver performance](#solver-performance).
> Themed visuals and the Playwright suite are still to come.

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
| `tools/fit-success-model.py` | Refits the solver's success model from recorded games |

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

## Solver performance

The brief asks for a program "reliably able to achieve a score of at least 1000 points". *Reliably*
is a claim about a distribution, so the answer here is one rather than a screenshot of a good run.

**46 games, played end to end against the live API, at the configuration this repository ships:**

| min | p25 | median | p75 | p95 | max | mean |
|---:|---:|---:|---:|---:|---:|---:|
| 2700 | 3647 | 4329 | 5175 | 5801 | 6484 | 4407 |

**None of the 46 finished below 1000.** The worst game scored 2.7× the bar, so the result does not
rest on a confidence interval around a threshold nothing came near.

Every game ended by running out of viable moves rather than by dying: broke, with a board offering
only labels it will not touch. The score is therefore what a game earned before it ran out of moves.
Getting past that would mean giving the solver a longer horizon than the one turn it plans over
today, which is a change of approach rather than a tuning.

### Reproducing it

```bash
cd api && ./gradlew bench -Pgames=34
```

A `bench` Spring profile plays whole games in process — no HTTP layer, no browser — through the same
service the app's auto-play button drives, so what is measured is the bot as shipped. It prints the
distribution and writes one CSV row per solve. Every upstream call passes a shared pacer that widens
itself whenever the upstream rate-limits, because Cloudflare limits on burst.

Both tunable records can be swept from the command line without a rebuild:

```bash
./gradlew bench -Pgames=12 -Pargs="--bench.label=slower --bench.strategy.target-level-per-turn=0.1"
```

### How the success model was fitted

The solver scores an ad as `reward × p − lifeCost × (1 − p)`, where `p` is
`P(success | label, reward, level)`. That estimate is fitted, not assumed:

```bash
python tools/fit-success-model.py api/build/bench/attempts-*.csv
```

Maximum likelihood over 2,486 recorded solves plus the exploration table in `docs/api-findings.md`,
split into training and held-out sets **by game** rather than by row, since attempts within one game
are heavily correlated. Held-out log-likelihood improved from −339 to −270 and Brier score from
0.127 to 0.098. Stdlib only — no numpy, no scipy.

The refit mattered more for the floor than the median. Ads worth more than 1.2× the old model's
ceiling had been given a 0.29 chance and turned out to succeed **0.08 of the time across 265
attempts**; once the solver stopped attempting them it stopped dying to them, and deaths across a
round of games went from 4 in 16 to 0 in 12. Full numbers are in `docs/api-findings.md`.

## Tests

```bash
cd api && ./gradlew test
```

```bash
cd web && npm run test:run
```

Coverage reports: `./gradlew jacocoTestReport` (`api/build/reports/jacoco`) and
`npm run test:coverage`. Both suites run in CI on every push.
