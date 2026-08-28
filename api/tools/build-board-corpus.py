"""Builds the offline world's board corpus from a recorded benchmark run.

The benchmark writes boards-<label>-<stamp>.csv under api/build/bench: one row per ad per turn,
taken from the whole board rather than from the ads the solver attempted. This turns that into
src/main/resources/offline/board-corpus.json, which CorpusBoardSource samples.

    python api/tools/build-board-corpus.py api/build/bench/boards-boards-*.csv

Rows are downsampled per (level, turn) bucket rather than uniformly, so the sparse corners of the
recording survive the trim instead of being averaged away by the crowded ones. The message pool is
capped too: the game builds ad text from a handful of templates filled with names and nouns, so
thousands of distinct strings carry no more variety than a fraction of them, and keeping all of
them would be most of the file.
"""

import csv
import glob
import json
import statistics
import sys
from collections import Counter, defaultdict
from pathlib import Path

LEVEL_BAND = 4
TURN_BAND = 20
PER_BUCKET = 250
MESSAGES = 1200

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/offline/board-corpus.json"


def bucket(level, turn):
    return min(level // LEVEL_BAND, 15), min(turn // TURN_BAND, 20)


def main(patterns):
    files = sorted({f for pattern in patterns for f in glob.glob(pattern)})
    if not files:
        sys.exit(f"no board files matched {patterns}")

    rows = []
    for path in files:
        with open(path, newline="", encoding="utf-8") as handle:
            rows.extend(list(csv.DictReader(handle)))
    if not rows:
        sys.exit("board files are empty")

    games = {row["game"] for row in rows}
    per_board = Counter((row["game"], row["turn"]) for row in rows)
    board_size = round(statistics.median(per_board.values()))

    labels = sorted({row["label"] for row in rows})
    label_index = {label: i for i, label in enumerate(labels)}
    seen = sorted({row["message"] for row in rows})
    messages = seen[:: max(1, len(seen) // MESSAGES)][:MESSAGES]

    buckets = defaultdict(list)
    for row in rows:
        level, turn = int(row["level"]), int(row["turn"])
        buckets[bucket(level, turn)].append(
            [level, turn, int(row["reward"]), label_index[row["label"]]]
        )

    entries = []
    for key in sorted(buckets):
        held = buckets[key]
        # Evenly spaced rather than the first N: a bucket is filled in turn order, and taking its
        # head would keep only the earliest game to reach it.
        step = max(1, len(held) // PER_BUCKET)
        entries.extend(held[::step][:PER_BUCKET])

    corpus = {
        "games": len(games),
        "boards": len(per_board),
        "boardSize": board_size,
        "labels": labels,
        "messages": messages,
        "entries": entries,
    }

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(corpus, separators=(",", ":")), encoding="utf-8")

    print(f"{len(rows)} ads over {len(per_board)} boards in {len(games)} games")
    print(f"board size {board_size}, {len(labels)} labels, "
          f"{len(messages)} of {len(seen)} messages kept")
    print(f"{len(entries)} entries kept in {len(buckets)} buckets -> {OUT} "
          f"({OUT.stat().st_size / 1024:.0f} KB)")


if __name__ == "__main__":
    main(sys.argv[1:] or [str(ROOT / "build/bench/boards-*.csv")])
