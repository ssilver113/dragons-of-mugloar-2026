#!/usr/bin/env python3
"""Measure every theme colour as text against the surfaces it can land on.

The palette in style.css is written in oklch, which says nothing about whether a pairing is
readable. This converts each token to sRGB and reports the WCAG 2.1 contrast ratio, so a palette
change is accepted on a number rather than on how it looked on one monitor.

Usage: python tools/check-contrast.py [path/to/style.css]

Exits non-zero if any required pairing falls below its threshold.
"""

from __future__ import annotations

import math
import re
import sys
from pathlib import Path

# Text below 4.5:1 fails AA. Large text (>=24px, or >=18.66px bold) is held to 3.0:1; the only
# tokens that qualify are the ones used solely on headings, and none are, so 4.5 applies to all.
AA_TEXT = 4.5
# Borders, rules and icon strokes are non-text contrast: AA asks 3.0:1 for anything a user has to
# perceive to operate the control.
AA_NON_TEXT = 3.0


def oklch_to_linear_srgb(lightness: float, chroma: float, hue_deg: float) -> tuple[float, float, float]:
    """oklch -> linear-light sRGB, via OKLab. Values may fall outside [0,1] when out of gamut."""
    hue = math.radians(hue_deg)
    a = chroma * math.cos(hue)
    b = chroma * math.sin(hue)

    l_ = lightness + 0.3963377774 * a + 0.2158037573 * b
    m_ = lightness - 0.1055613458 * a - 0.0638541728 * b
    s_ = lightness - 0.0894841775 * a - 1.2914855480 * b

    l, m, s = l_**3, m_**3, s_**3

    return (
        +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
    )


def encode(channel: float) -> float:
    """Linear-light -> sRGB gamma encoded, clamped. Clamping is the gamut clip a browser performs."""
    c = min(max(channel, 0.0), 1.0)
    return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055


def decode(channel: float) -> float:
    """sRGB gamma encoded -> linear-light, which is what the luminance formula is defined over."""
    return channel / 12.92 if channel <= 0.04045 else ((channel + 0.055) / 1.055) ** 2.4


class Colour:
    def __init__(self, name: str, lightness: float, chroma: float, hue: float) -> None:
        self.name = name
        self.srgb = tuple(encode(c) for c in oklch_to_linear_srgb(lightness, chroma, hue))
        # Round-trip through 8-bit: the ratio a display actually produces, not the ideal one.
        self.rgb8 = tuple(round(c * 255) for c in self.srgb)

    @property
    def hex(self) -> str:
        return "#%02x%02x%02x" % self.rgb8

    @property
    def luminance(self) -> float:
        r, g, b = (decode(c / 255) for c in self.rgb8)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b


def ratio(a: Colour, b: Colour) -> float:
    hi, lo = max(a.luminance, b.luminance), min(a.luminance, b.luminance)
    return (hi + 0.05) / (lo + 0.05)


TOKEN = re.compile(
    r"--color-([a-z-]+):\s*oklch\(\s*([\d.]+)%\s+([\d.]+)\s+([\d.]+)\s*\)",
)


def read_tokens(css: str) -> dict[str, Colour]:
    found = {}
    for name, lightness, chroma, hue in TOKEN.findall(css):
        found[name] = Colour(name, float(lightness) / 100, float(chroma), float(hue))
    return found


def main() -> int:
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "web/src/style.css")
    tokens = read_tokens(path.read_text(encoding="utf-8"))

    missing = {"surface", "surface-raised", "ink", "ink-muted", "accent", "danger", "warning", "success"} - tokens.keys()
    if missing:
        print(f"missing tokens in {path}: {', '.join(sorted(missing))}", file=sys.stderr)
        return 2

    print(f"{path}\n")
    for name in sorted(tokens):
        print(f"  --color-{name:<16} {tokens[name].hex}")

    # Text sits on both surfaces. On a light theme the darker of the two is the hard case, which
    # is the opposite of the dark theme this palette replaced -- so both are reported and the
    # threshold is applied to the worse one rather than to a surface chosen by hand.
    surfaces = [tokens["surface"], tokens["surface-raised"]]
    foregrounds = ["ink", "ink-muted", "accent", "danger", "warning", "success"]

    print("\n  as text, against both surfaces:\n")
    worst = ("", AA_TEXT * 10)
    failures = []
    for name in foregrounds:
        ratios = [ratio(tokens[name], s) for s in surfaces]
        low = min(ratios)
        mark = "ok " if low >= AA_TEXT else "FAIL"
        print(f"  {mark} {name:<16} surface {ratios[0]:5.2f}:1   raised {ratios[1]:5.2f}:1")
        if low < AA_TEXT:
            failures.append(name)
        if low < worst[1]:
            worst = (name, low)

    # The primary control is accent-filled with surface-coloured text on it, which no
    # foreground-on-surface check covers.
    inverse = ratio(tokens["accent"], tokens["surface"])
    mark = "ok " if inverse >= AA_TEXT else "FAIL"
    print(f"\n  {mark} surface text on an accent fill      {inverse:5.2f}:1")
    if inverse < AA_TEXT:
        failures.append("surface-on-accent")

    # Borders and rules: perceivable, but never carrying text.
    edge = ratio(tokens["ink-muted"], tokens["surface"])
    mark = "ok " if edge >= AA_NON_TEXT else "FAIL"
    print(f"  {mark} ink-muted as a border on surface    {edge:5.2f}:1")
    if edge < AA_NON_TEXT:
        failures.append("ink-muted border")

    if failures:
        print(f"\n  {len(failures)} below threshold: {', '.join(failures)}")
        return 1
    print(f"\n  lowest is {worst[0]} at {worst[1]:.2f}:1")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
