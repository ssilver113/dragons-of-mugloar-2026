#!/usr/bin/env python3
"""Refit the solver's success model against a corpus of real solve attempts.

The model is `P(success) = prior(tier) x logistic(reward, level)`, with four parameters:

    ceiling(level)  = ceilingBase + ceilingPerLevel * level
    feasibility     = 1 / (1 + exp((reward - midpointFactor * ceiling) / (softnessFactor * ceiling)))

Its first fit came from a few hundred hand-driven attempts at levels 0-12 and turns under 50, and
it goes optimistic outside that range. This script fits the same four parameters by maximum
likelihood over `bench` output instead, which reaches levels and turns no hand-played game does.

Games, not rows, are split into train and held-out sets: rows from one game are heavily correlated,
so splitting rows would let the two halves see the same game and report an improvement that is not
there.

`midpointFactor` is held at its current value unless `--free-midpoint` is passed. The four
parameters are one dimension redundant — scaling the two ceiling terms up while scaling the two
factors down describes the identical curve — so a free fit wanders along that ridge and lands on an
arbitrary representative. Worse, `safeRewardCeiling` is reused as the threshold behind the UI's
"out of league" flag, so letting the fit trade the ceiling against the midpoint would move a flag
the player sees without changing a single prediction.

The recon table from `docs/api-findings.md` is folded in as weighted pseudo-rows unless
`--no-recon` is passed. The solver never attempts a rich ad at a low level — its own model tells it
not to — so the benchmark corpus has no data in that corner and a fit over it alone extrapolates
there, contradicting the one place the hand-driven exploration did measure. Recon's counts are
known, so its cells go in as the observations they are, at their measured weight.

**Both corpora are selection-biased and the fitted model inherits it.** The solver only attempts ads it
already scores favourably, so these rows describe the ads it chooses rather than the whole board.
That is the right corpus for making the solver's own estimates truthful, and the wrong one for
claiming a general law about the game.

Usage:
    python tools/fit-success-model.py api/build/bench/attempts-*.csv
"""

import argparse
import csv
import glob
import math
import zlib

# The priors the solver carries, one per tier. Held fixed here: this script fits how much of a
# prior survives a rich ad, not what the prior is.
PRIORS = {
    "SAFE": 0.87,
    "FAVOURABLE": 0.74,
    "EVEN": 0.41,
    "POOR": 0.23,
    "DOOMED": 0.03,
    "UNKNOWN": 0.0,
}

CURRENT = (100.0, 12.0, 1.25, 0.18)

# docs/api-findings.md, top-tier labels by level band and reward band: the level and reward taken as
# representative of each cell, the measured rate, and the number of attempts behind it. The level-0
# 200+ cell is left out — one attempt says nothing. These are the only measurements covering rich
# ads at a low level, because that is the corner the solver's own model keeps it out of.
RECON = [
    (0, 75, 0.93, 235), (0, 125, 0.50, 26), (0, 175, 0.00, 61),
    (4, 75, 0.94, 50), (4, 125, 0.88, 17), (4, 175, 0.50, 10), (4, 230, 0.31, 13),
    (12, 125, 0.70, 10), (12, 175, 0.94, 49), (12, 230, 0.90, 21),
]


def recon_rows():
    rows = []
    for level, reward, rate, n in RECON:
        hits = round(rate * n)
        rows += [("SAFE", reward, level, 1)] * hits
        rows += [("SAFE", reward, level, 0)] * (n - hits)
    return rows
NAMES = ("ceilingBase", "ceilingPerLevel", "midpointFactor", "softnessFactor")
EPS = 1e-9


def estimate(params, tier, reward, level):
    ceiling_base, ceiling_per_level, midpoint_factor, softness_factor = params
    ceiling = ceiling_base + ceiling_per_level * max(0, level)
    midpoint = ceiling * midpoint_factor
    softness = ceiling * softness_factor
    z = (reward - midpoint) / softness
    # exp overflows long before the probability stops being zero to every digit that matters.
    feasibility = 0.0 if z > 700 else 1.0 / (1.0 + math.exp(z))
    return PRIORS.get(tier, 0.0) * feasibility


def log_likelihood(params, rows):
    total = 0.0
    for tier, reward, level, success in rows:
        p = min(1.0 - EPS, max(EPS, estimate(params, tier, reward, level)))
        total += math.log(p) if success else math.log(1.0 - p)
    return total


def brier(params, rows):
    if not rows:
        return float("nan")
    return sum((estimate(params, t, r, l) - s) ** 2 for t, r, l, s in rows) / len(rows)


def valid(params):
    ceiling_base, ceiling_per_level, midpoint_factor, softness_factor = params
    return (
        ceiling_base > 1.0
        and ceiling_per_level >= 0.0
        and midpoint_factor > 0.05
        and softness_factor > 0.01
    )


def fit(rows, start=CURRENT, free_midpoint=False):
    """Compass search: step each parameter both ways, halve the steps when nothing improves.

    Deterministic and dependency-free, which matters more here than speed — the surface is smooth
    and low-dimensional, and the whole fit takes a fraction of a second.
    """
    best = list(start)
    best_ll = log_likelihood(best, rows)
    steps = [40.0, 4.0, 0.20 if free_midpoint else 0.0, 0.05]

    for _ in range(400):
        improved = False
        for i in range(len(best)):
            if steps[i] == 0.0:
                continue
            for direction in (1, -1):
                candidate = list(best)
                candidate[i] += direction * steps[i]
                if not valid(candidate):
                    continue
                ll = log_likelihood(candidate, rows)
                if ll > best_ll + 1e-9:
                    best, best_ll = candidate, ll
                    improved = True
        if not improved:
            steps = [s / 2 for s in steps]
            if max(steps) < 1e-4:
                break
    return tuple(best), best_ll


def load(paths):
    rows = []
    for path in paths:
        with open(path, newline="", encoding="utf-8") as handle:
            for row in csv.DictReader(handle):
                rows.append(
                    (
                        row["game"],
                        row["tier"],
                        int(row["reward"]),
                        int(row["level"]),
                        int(row["turn"]),
                        int(row["success"]),
                    )
                )
    return rows


def split(rows, holdout_share=0.25):
    """Split on the game id, so no game contributes to both halves."""
    train, held = [], []
    for game, tier, reward, level, _turn, success in rows:
        bucket = zlib.crc32(game.encode()) % 100
        (held if bucket < holdout_share * 100 else train).append(
            (tier, reward, level, success)
        )
    return train, held


def band(value, edges):
    for i, edge in enumerate(edges):
        if value < edge:
            return i
    return len(edges)


def calibration_table(rows, params, label, edges, key):
    buckets = {}
    for tier, reward, level, success in rows:
        bucket = band(key(reward, level), edges)
        hits, total, predicted = buckets.get(bucket, (0, 0, 0.0))
        buckets[bucket] = (
            hits + success,
            total + 1,
            predicted + estimate(params, tier, reward, level),
        )

    names = [f"<{edges[0]}"] + [
        f"{lo}-{hi}" for lo, hi in zip(edges, edges[1:])
    ] + [f"{edges[-1]}+"]

    print(f"\n  {label:<12} {'n':>6} {'observed':>9} {'predicted':>10}  gap")
    for bucket in sorted(buckets):
        hits, total, predicted = buckets[bucket]
        observed = hits / total
        mean = predicted / total
        flag = "  <-- optimistic" if mean - observed > 0.08 else ""
        print(
            f"  {names[bucket]:<12} {total:>6} {observed:>9.2f} {mean:>10.2f}"
            f"  {mean - observed:+.2f}{flag}"
        )


def report(name, params, train, held):
    print(f"\n{name}")
    print("  " + "  ".join(f"{n}={v:.4g}" for n, v in zip(NAMES, params)))
    print(
        f"  train  log-likelihood {log_likelihood(params, train):>10.1f}"
        f"   Brier {brier(params, train):.4f}   n={len(train)}"
    )
    print(
        f"  held   log-likelihood {log_likelihood(params, held):>10.1f}"
        f"   Brier {brier(params, held):.4f}   n={len(held)}"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("corpus", nargs="+", help="attempts CSV files, globs allowed")
    parser.add_argument(
        "--holdout", type=float, default=0.25, help="share of games held out (default 0.25)"
    )
    parser.add_argument(
        "--no-recon",
        action="store_true",
        help="fit the benchmark corpus alone, without the recon table's measurements",
    )
    parser.add_argument(
        "--free-midpoint",
        action="store_true",
        help="also fit midpointFactor. Redundant with the ceiling terms — see the module docstring",
    )
    args = parser.parse_args()

    paths = sorted({p for pattern in args.corpus for p in glob.glob(pattern)})
    if not paths:
        raise SystemExit("No corpus files matched")

    rows = load(paths)
    train, held = split(rows, args.holdout)
    print(f"{len(rows)} attempts from {len(paths)} file(s)")
    print(f"  overall success rate {sum(r[5] for r in rows) / len(rows):.3f}")
    print(f"  {len(train)} training rows, {len(held)} held out on {args.holdout:.0%} of games")

    # Recon joins the training set only. Scoring it against a held-out set of benchmark games is
    # the honest comparison — a model must not be credited for reciting rows it was handed.
    if not args.no_recon:
        recon = recon_rows()
        train = train + recon
        print(f"  plus {len(recon)} recon rows, covering rich ads at low level")

    report("Current model", CURRENT, train, held)
    fitted, _ = fit(train, free_midpoint=args.free_midpoint)
    report("Refitted", fitted, train, held)

    print("\nWhere the current model goes wrong")
    calibration_table(train, CURRENT, "level", [4, 8, 12, 18], lambda r, l: l)
    calibration_table(
        train, CURRENT, "reward/ceil", [0.5, 0.9, 1.2], lambda r, l: r / (100 + 12 * l)
    )

    print("\nThe same bands under the refit")
    calibration_table(train, fitted, "level", [4, 8, 12, 18], lambda r, l: l)
    calibration_table(
        train, fitted, "reward/ceil", [0.5, 0.9, 1.2], lambda r, l: r / (100 + 12 * l)
    )

    print("\nPaste into SuccessModel:")
    print(
        "    public static final SuccessModel MEASURED = new SuccessModel("
        + ", ".join(f"{v:.4g}" for v in fitted)
        + ");"
    )


if __name__ == "__main__":
    main()
