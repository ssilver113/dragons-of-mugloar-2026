# Mugloar API — verified behaviour

Everything here was confirmed against the live API on 2026-08-21, not taken from the published docs. Where the two disagree, the observed behaviour is authoritative and the discrepancy is called out. Sample sizes are stated so the numbers can be judged.

Evidence base: **1334 recorded solve attempts** across 19 full games, plus targeted probes for error handling, shop effects and expiry semantics.

## Endpoints

Base URL `https://dragonsofmugloar.com/api/v2`.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/game/start` | Start a game |
| `GET` | `/{gameId}/messages` | List the message board |
| `POST` | `/{gameId}/solve/{adId}` | Attempt an ad |
| `GET` | `/{gameId}/shop` | List shop items |
| `POST` | `/{gameId}/shop/buy/{itemId}` | Buy an item |
| `POST` | `/{gameId}/investigate/reputation` | Read reputation |

### Response shapes

```jsonc
// POST /game/start
{"gameId":"IeLKvlDb","lives":3,"gold":0,"level":0,"score":0,"highScore":0,"turn":0}

// GET /{gameId}/messages  — a bare array, one entry per ad
[{"adId":"LTyNBlYB","message":"Help Robin Webster to …","reward":15,
  "expiresIn":7,"encrypted":null,"probability":"Piece of cake"}]

// POST /{gameId}/solve/{adId}
{"success":true,"lives":3,"gold":17,"score":17,"highScore":0,"turn":3,
 "message":"You successfully solved the mission!"}

// GET /{gameId}/shop — a bare array
[{"id":"hpot","name":"Healing potion","cost":50}]

// POST /{gameId}/shop/buy/{itemId}
{"shoppingSuccess":true,"gold":13,"lives":4,"level":0,"turn":3}

// POST /{gameId}/investigate/reputation
{"people":0,"state":0,"underworld":0}
```

### Where the published docs are wrong

The docs are served by apiDoc at `/doc/`; the page is client-rendered, so the schema has to be read out of `assets/main.bundle.js` rather than fetched as JSON. Having done that, five discrepancies matter:

1. **`/messages` and `/shop` return bare arrays**, not the documented `{messages:[…]}` / `{items:[…]}` wrappers.
2. **`reward` is a number**, documented as `String`. 5130/5130 observed values were integers.
3. **`shoppingSuccess` is a boolean**, documented as `String`.
4. **`probability` and `encrypted` are undocumented entirely** — and they are the two fields the whole game turns on.
5. **`highScore` is always `0`.** Never observed non-zero across 1334 solves, including a game that reached 2662. Treat it as a dead field; do not surface it.

## Encryption

`encrypted` is `null`, `1`, or `2` (integer). Observed frequency: 4759 / 343 / 28 across 5130 board entries.

- `1` → **Base64**
- `2` → **ROT13** (letters only; digits pass through unchanged)

The cipher applies to **`adId`, `message` and `probability` together**. `reward`, `expiresIn` and `encrypted` are never encoded. All 371 encrypted entries decoded cleanly under these two schemes — zero failures.

**The `adId` must be decoded before it is used.** Posting the encoded `adId` to `/solve` returns `400 Bad Request`; posting the decoded one solves the ad normally. This is the single most consequential undocumented detail in the API.

No encrypted ad appeared in 150 level-0 board entries, so encryption is gated on progression rather than sprinkled randomly.

Whether encrypted ads carry different odds than plaintext ones is **not established** — only 371 were observed and few were attempted. Nothing suggests a difference; the solver should not assume one either way.

## Probability labels

Exactly **11** distinct labels exist. Measured success rates, from the exploration run that sampled labels without bias (n = 992):

| Label | n | Success |
|---|---:|---:|
| Sure thing | 50 | 0.88 |
| Walk in the park | 99 | 0.88 |
| Piece of cake | 163 | 0.87 |
| Quite likely | 59 | 0.76 |
| Hmmm.... | 60 | 0.72 |
| Gamble | 43 | 0.44 |
| Risky | 41 | 0.37 |
| Rather detrimental | 15 | 0.27 |
| Playing with fire | 34 | 0.21 |
| Suicide mission | 128 | 0.05 |
| Impossible | 300 | **0.00** |

`Impossible` failed 300 times out of 300. `Suicide mission` succeeded 7 times in 128. Neither is ever worth a turn.

The labels fall into **tiers, and the tiers are what's reliable — the order within a tier is not.** `Sure thing` / `Walk in the park` / `Piece of cake` are statistically indistinguishable at 0.88/0.88/0.87, as are `Quite likely` / `Hmmm....` at 0.76/0.72. An earlier, smaller sample (n=866) ranked them differently in both cases; that ordering did not survive more data, so the solver should treat tiers as equivalence classes rather than trusting a strict ranking. These pooled rates also average over the level effect described next, which is the larger source of variation.

## The label is not an absolute probability

**This is the finding that drives the solver design.** A label's real success rate depends on the ad's `reward` relative to the dragon's `level`.

Top-three labels only (`Sure thing`, `Piece of cake`, `Walk in the park`), by reward band and level:

| Level | reward <100 | 100–150 | 150–200 | 200+ |
|---|---:|---:|---:|---:|
| 0 | 0.93 (n=235) | 0.50 (n=26) | **0.00 (n=61)** | 0.00 (n=1) |
| 2–6 | 0.94 (n=50) | 0.88 (n=17) | 0.50 (n=10) | 0.31 (n=13) |
| 12 | — | 0.70 (n=10) | 0.94 (n=49) | 0.90 (n=21) |

A `Piece of cake` worth 180 gold is a **certain loss at level 0** and roughly a 0.9 bet at level 12. Tracked over turns at a fixed level 0, `Piece of cake` decays from 0.95 in turns 0–10 to 0.04 in turns 50–100 — because the board's reward scale climbs while the dragon does not.

The reward band matters only *relative to level*: the same 150–200 band runs 0.00 at level 0, 0.50 at levels 2–6 and 0.94 at level 12. Note that a naive within-label tercile split, pooled across levels, shows only a mild decline (`Piece of cake`: 0.93 / 0.89 / 0.78) — pooling hides the effect, because high-reward ads are mostly drawn at high level where they are safe. The banded-by-level table above is the clean view and the one to design against.

Approximate safe-reward ceiling: **~100 at level 0, ~150 at levels 2–6, ~250+ at level 12** — roughly `100 + 12·level`. That linear fit came from the exploration alone, over levels 0–12 and turns under 50. **It has since been superseded — see the refit below.**

### The ceiling refitted, from 2,486 recorded solves

The headless benchmark plays whole games through the solver and logs every attempt with the state it was scored against, which reaches levels and turn counts no hand-driven exploration visits. Fitting the same curve by maximum likelihood over 2,486 of those attempts, together with the table above at its measured weight, gives a materially different shape:

| | level 0 | level 4 | level 12 | level 18 | level 26 |
|---|---:|---:|---:|---:|---:|
| exploration fit, `100 + 12·level` | 100 | 148 | 244 | 316 | 412 |
| refit, `112 + 8.05·level` | 112 | 145 | 209 | 257 | 322 |

**The ceiling starts higher and climbs slower.** The first fit was mildly pessimistic at low level and badly optimistic at high level, which is exactly what long runs kept showing: games reaching level 18 and beyond failed strings of solves they had scored favourably.

**The curve is also far sharper than assumed.** The logistic's width fell from 0.18 of the ceiling to 0.066, so the estimate falls from most of the label's prior to nearly nothing across a band about a tenth of the ceiling wide. The game behaves much more like a threshold than like a slope. Concretely, ads more than 1.2× the old ceiling were given 0.29 by the first fit and came in at **0.08 across 265 attempts**.

After the refit, mean prediction tracks observed rate within 0.04 in every level band and every richness band. The corpus behind it is reproducible — the harness writes one attempt per row, with the state each was scored against, to `api/build/bench/attempts-*.csv`:

```bash
cd api && ./gradlew bench -Pgames=40
```

Fitting the curve to those rows is a maximum-likelihood estimate over four parameters; the fitted values and the calibration they produce are recorded on `SuccessModel`, which is where the solver reads them from.

**Caveat that travels with these numbers.** Both corpora are selection-biased: the solver only attempts ads it already scores favourably, and the exploration sampled by hand. The refit is therefore the right basis for making the solver's own estimates truthful, and the wrong basis for claiming a general law about the game. The two are pooled precisely because they are biased in different directions — the solver avoids rich ads at low level, and that corner is the one the exploration measured.

**Consequence:** `EV = reward × P(label)` is wrong. The estimator needs `P(success | label, reward, level)`. And levelling is not optional — a bot that never upgrades decays to near-certain failure, because the board outgrows it.

## Level, lives and the shop

`level` appears **only** in `/game/start` and `/shop/buy` responses. It is absent from `/solve`, so a client that wants to display it must track it from purchases.

Measured effects, each from a fresh game:

| Item | Cost | Effect |
|---|---:|---|
| `hpot` Healing potion | 50 | **+1 life**, no level change |
| `cs`, `gas`, `wax`, `tricks`, `wingpot` | 100 | **level +1** each |
| `ch`, `rf`, `iron`, `mtrix`, `wingpotmax` | 300 | **level +2** each |

Every 100-gold item behaves identically (+1 level) and every 300-gold item behaves identically (+2 levels); the names are flavour.

**The two tiers trade gold against turns, and neither dominates.** Three 100-gold items cost 300 gold and three turns for **+3 levels**; one 300-gold item costs 300 gold and one turn for **+2 levels**. So the 100-gold tier is 1.5× the level per *gold*, and the 300-gold tier is 2× the level per *turn*. Which is the better buy depends on which resource is scarcer at that moment, so it is a strategy parameter to tune against the benchmark, not a fixed rule.

**Lives are uncapped.** Bought `hpot` four times consecutively from full health and reached 7 lives with no ceiling and no diminishing effect.

`score` is cumulative gold *earned* and never decreases; `gold` is the current balance and falls when you spend. Verified monotonic across all recorded games.

## Turn and expiry semantics

- `expiresIn` starts at 7 and decrements by 1 **every turn**, whichever action consumed it.
- **A purchase consumes a turn — including a failed one.** Buying with insufficient gold still advanced the turn and still aged every ad by 1. Confirmed: all 10 ads went 7 → 6 across a purchase that returned `shoppingSuccess:false`.
- An ad is gone from the board once it expires; it is not returned with `expiresIn:0`.
- **A failed ad also leaves the board**, immediately — it is not offered again for a second try. Confirmed on four failures across four games (`Rather detrimental`, `Risky`, `Hmmm....`, `Suicide mission`); in every case the ad was absent from the very next `/messages`. So a client never has to de-duplicate against its own attempts to avoid picking one twice.
- Solving an ad twice returns `400 Bad Request`.

The turn cost of shopping is a real strategic cost, not bookkeeping: every potion bought is an ad not solved and 1 expiry tick against every ad on the board.

## Errors

Error handling cannot assume JSON. **Most failures return an HTML error page**, not a JSON body.

| Case | Status | Body |
|---|---|---|
| Unknown `gameId` (any endpoint) | `404` | HTML `Not Found` |
| Unknown or already-solved `adId` | `400` | HTML `Bad Request` |
| Encoded `adId` sent for an encrypted ad | `400` | HTML `Bad Request` |
| Unknown `itemId` | `200` | JSON `{"shoppingSuccess":false,…}` — **and the turn still advances** |
| Insufficient gold | `200` | JSON `{"shoppingSuccess":false,…}` — turn still advances |
| Any call after lives reach 0 | `410` | JSON `{"status":"Game Over"}` |

Two traps: a failed purchase is a `200` that must be detected by reading `shoppingSuccess`, and game-over is the one error path that *is* JSON. A client that parses every non-2xx as JSON will throw on the HTML pages; one that trusts `200` will silently miss failed purchases.

## Cloudflare user-agent filtering and rate limiting

The API sits behind Cloudflare, which turns callers away for two separate reasons: a browser-signature ban (**`403`, body `error code: 1010`**) and rate limiting (**`429`, error 1015**).

| User-Agent | Result |
|---|---|
| `Python-urllib/3.12` | **403 / 1010** |
| `Java/21.0.12` | 200 |
| `Apache-HttpClient/5.3` | 200 |
| `curl/8.4.0` | 200 |
| `dragons-of-mugloar-client/1.0` | 200 |
| *(empty)* | 200 |

Only the `Python-urllib` signature was blocked, so the JDK client works untouched. The backend should still send an explicit descriptive `User-Agent` so this cannot become a silent failure if the rules change.

**Rate limiting is real, and the original exploration missed it.** Roughly 3000 requests spread over an hour drew no `429` at all, which is why this document first recorded that there was none. An automated player is a different traffic shape: three calls per turn with no pause between them trips `429` with a Cloudflare **error 1015** body after a few hundred turns. The limit is on the burst, not the hourly total.

Two consequences. A client must classify `429` separately from other failures — it is neither a server fault nor a game error, and the only correct response is to wait. And it must never retry into it, which would turn a pause into a ban.

No authentication is required.

## Solve result messages

Exactly three strings, suitable for direct display:

- `You successfully solved the mission!`
- `You failed on the mission!`
- `You were defeated on your last mission!` (accompanies `lives: 0`)

## Reputation

`POST /{gameId}/investigate/reputation` returns `{people, state, underworld}`, starting at `0/0/0`. Whether reputation influences ad availability or success rates was **not tested** — deferred until the benchmark can measure whether it moves the score distribution.

Its turn cost was measured exactly, because it is the one action whose response carries **no game state at all**: every ad aged `7 → 6` and the next solve reported turn 2, with lives, gold, level and score untouched. So it costs precisely one turn and moves nothing else, and a client that wants to keep tracking state must apply that turn itself.

That makes it the only move that spends a turn without risking a life — useful as a deliberate pass when no ad is worth attempting.
