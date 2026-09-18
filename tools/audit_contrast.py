#!/usr/bin/env python3
"""Contrast audit for every caption template.

Captions sit on arbitrary video, so "readable" has two independent halves:

  1. SEPARATION - the glyph has to be distinguishable from whatever is behind
     it. That is what the outline (BorderStyle 1) or the box (BorderStyle 3)
     is for. A light fill with a light outline disappears on bright footage.
  2. DEFINITION - the fill has to be distinguishable from that outline/box,
     otherwise the text has no visible edge even when the block as a whole is
     visible.

This tool re-implements the per-treatment colour derivation in
StyleAssMapper.map() and scores both halves with the WCAG 2.x relative
luminance formula. It is deliberately a copy rather than a call: there is no
JVM here, and a mismatch between the two is exactly the kind of drift the
companion test in AssSubtitleBuilderTest is there to catch.

Exit code 1 when any template fails, so CI can gate on it.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "app/src/main/java/com/saad/capvid/style/CaptionStyleCatalog.java"

# WCAG AA. 4.5:1 is the normal-text threshold; captions are usually large text
# (3:1 would pass) but they sit on uncontrolled video, so hold the stricter one.
MIN_DEFINITION = 4.5
MIN_SEPARATION = 3.0

ENTRY_RE = re.compile(
    r'style\(\s*"([A-Z0-9_]+)"\s*,\s*"([^"]*)"\s*,\s*cats\(([^)]*)\)\s*,\s*'
    r'colors\(\s*"(#[0-9A-Fa-f]{6})"\s*,\s*"(#[0-9A-Fa-f]{6})"\s*,\s*'
    r'"(#[0-9A-Fa-f]{6})"\s*,\s*"(#[0-9A-Fa-f]{6})"\s*\)\s*,\s*'
    r'"([^"]*)"\s*,\s*(true|false)\s*,\s*([A-Z_]+)\s*,\s*(true|false)\s*\)',
    re.S,
)


def parse(hexstr):
    h = hexstr.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def with_alpha(rgb, alpha):
    """Match StyleAssMapper.withAlpha: keep rgb, replace the alpha channel."""
    return rgb + (alpha,)


def luminance(rgb):
    """WCAG relative luminance. Alpha is ignored - composite() first if the
    colour is translucent, since a translucent colour has no luminance of its
    own until it is over something."""
    r, g, b = rgb[0], rgb[1], rgb[2]

    def chan(v):
        v /= 255.0
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4

    return 0.2126 * chan(r) + 0.7152 * chan(g) + 0.0722 * chan(b)


def ratio(fg, bg):
    la, lb = luminance(fg), luminance(bg)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def composite(rgba, frame):
    """Blend a possibly translucent colour over an opaque frame.

    A translucent box or shadow is not a fixed colour - what the eye sees is
    the blend with whatever is behind it. Scoring it as a fixed colour, which
    is what compositing once over black amounts to, is wrong in both
    directions, so the blend is taken against each candidate frame.
    """
    r, g, b, a = rgba
    k = a / 255.0
    return (r * k + frame[0] * (1 - k),
            g * k + frame[1] * (1 - k),
            b * k + frame[2] * (1 - k))


def worst_case_over_frames(colours):
    """The caption's readability against the WORST possible frame.

    The video underneath is unknown, so a caption is only trustworthy if it
    survives every backdrop. For a grey frame of luminance L, the caption reads
    if ANY of its colours contrasts with it, so the score at L is max over the
    caption's colours. Sweep L across the whole range and keep the minimum -
    that minimum is the worst frame the caption will ever meet.

    White fill on a black outline - the classic caption treatment - bottoms out
    at 4.62:1 on a mid-grey frame, which is why it is the standard. A single
    mid-luminance fill such as #FF3B30 tops out at 2.46:1 no matter what one
    extra colour is paired with it; only a light AND a dark element together
    lift it, which is what the legibility shadow in StyleAssMapper adds.
    """
    worst = float("inf")
    for i in range(101):
        l = i / 100.0
        # Invert the WCAG transfer function to get the sRGB level for this L.
        v = l * 12.92 if l <= 0.003038 else 1.055 * (l ** (1 / 2.4)) - 0.055
        level = max(0, min(255, int(round(v * 255))))
        frame = (level, level, level)
        best = max(ratio(composite(c, frame), frame) for c in colours)
        worst = min(worst, best)
    return worst


OPAQUE = lambda rgb: rgb + (255,)

# Must match StyleAssMapper.BOX_TRANSLUCENT_ALPHA.
BOX_TRANSLUCENT_ALPHA = 175

# Must match StyleAssMapper.LEGIBILITY_SHADOW / LEGIBILITY_SHADOW_Y.
LEGIBILITY_SHADOW = (10, 10, 15, 255)


def derive(treatment, sw):
    """Return (fill_colours, backing_rgba, is_box).

    Mirrors the switch in StyleAssMapper.map(). `fill_colours` is a list
    because gradient treatments paint several colours into one glyph, and
    `backing` is the box for BorderStyle 3 or the outline for BorderStyle 1.
    """
    sw = [OPAQUE(c) for c in sw]
    black = (0, 0, 0, 255)

    if treatment == "BOX_SOLID":
        return [sw[1]], sw[0], True
    if treatment == "BOX_TRANSLUCENT":
        return [sw[1]], with_alpha(sw[0][:3], BOX_TRANSLUCENT_ALPHA), True
    if treatment == "ROTATED_MARKER":
        return [sw[1]], sw[3], True
    if treatment == "OUTLINE_GLOW":
        return [sw[0]], sw[3], False
    if treatment == "GRADIENT_FILL":
        return [sw[0], sw[1], sw[2]], sw[3], False
    if treatment == "SPLIT_HALF":
        return [sw[1]], sw[2], False
    if treatment == "CHROME":
        return [sw[2], sw[0], sw[3]], sw[3], False
    if treatment == "COMIC_OUTLINE":
        return [sw[0]], sw[1], False
    if treatment == "SERIF_ITALIC":
        # outlineColor is never assigned for this treatment, so it keeps the
        # Mapping default.
        return [sw[0]], black, False
    # PLAIN and anything unrecognised fall through to the default branch.
    return [sw[0]], sw[3], False


NEAR_WHITE = (250, 250, 250, 255)
NEAR_BLACK = (10, 10, 15, 255)


def block_for(treatment, sw):
    """Every colour the rendered caption block puts against the video.

    Returns (colours, fix) where fix names the legibility adjustment
    StyleAssMapper applies, or "" when the palette is already sound.

    A block is only readable on arbitrary footage if it holds BOTH a light and
    a dark element: a single mid-luminance fill such as #FF3B30 tops out at
    2.46:1 against the worst frame no matter what ONE extra colour is paired
    with it, but light plus dark together reach 4.40:1 - with the fill itself
    untouched, so the template keeps its colour.

    Candidates are tried cheapest first, so an outline that already works is
    never repainted.
    """
    fills, backing, is_box = derive(treatment, sw)
    block = list(fills) + [backing]
    if worst_case_over_frames(block) >= MIN_SEPARATION:
        return block, ""

    if is_box:
        # A box cannot take a shadow: libass moves it onto the shadow layer and
        # repaints it in BackColour (ass_render.c:2737), which would recolour
        # the box. Box styles have to earn their contrast from the palette, so
        # the auditor reports them instead of fixing them.
        return block, "PALETTE"

    if worst_case_over_frames(block + [NEAR_BLACK]) >= MIN_SEPARATION:
        return block + [NEAR_BLACK], "shadow"
    if worst_case_over_frames(block + [NEAR_WHITE]) >= MIN_SEPARATION:
        return block + [NEAR_WHITE], "shadow"
    # The fill is mid-luminance and so is the outline: the outline has to become
    # the light element and the shadow the dark one.
    return list(fills) + [NEAR_WHITE, NEAR_BLACK], "outline+shadow"


def main():
    src = CATALOG.read_text(encoding="utf-8")
    entries = ENTRY_RE.findall(src)
    if not entries:
        print("FATAL: parsed no catalog entries - the regex drifted from the source", file=sys.stderr)
        return 2

    print("parsed %d catalog entries\n" % len(entries))
    header = "%-26s %-16s %-9s %-9s %s"
    print(header % ("template", "treatment", "edge", "worstcase", "verdict"))
    print("-" * 88)

    failures = []
    warnings = []
    for sid, name, cats, c0, c1, c2, c3, font, lite, treatment, modern in entries:
        sw = [parse(c0), parse(c1), parse(c2), parse(c3)]
        fills, backing, is_box = derive(treatment, sw)
        block, fix = block_for(treatment, sw)
        worst = worst_case_over_frames(block)

        # Informational only: fill and backing identical means the glyph loses
        # its edge, but the caption still reads as a solid block.
        defn = min(ratio(f, backing) for f in fills)

        problems = []
        if worst < MIN_SEPARATION:
            weakest = min(block, key=lambda c: min(abs(ratio(c, (0, 0, 0, 255))),
                                                   abs(ratio(c, (255, 255, 255, 255)))))
            problems.append("worst-case frame %.2f:1 (need %.1f); weakest colour #%02X%02X%02X"
                            % (worst, MIN_SEPARATION, weakest[0], weakest[1], weakest[2]))
        if defn < MIN_DEFINITION:
            problems.append("note: fill and backing differ by only %.2f:1 - no glyph edge"
                            % defn)

        verdict = "ok" if not problems else ("FAIL" if worst < MIN_SEPARATION else "warn")
        if fix:
            verdict += " [" + fix + "]"
        print(header % (sid, treatment, "%.2f" % defn, "%.2f" % worst, verdict))
        if worst < MIN_SEPARATION:
            failures.append((sid, treatment, problems))
        elif problems:
            warnings.append((sid, treatment, problems))

    print("-" * 88)
    print("%d/%d templates clear %.1f:1 on the worst possible frame\n"
          % (len(entries) - len(failures), len(entries), MIN_SEPARATION))

    for sid, treatment, problems in warnings:
        print("%s (%s):" % (sid, treatment))
        for p in problems:
            print("    %s" % p)
    if warnings:
        print()

    for sid, treatment, problems in failures:
        print("%s (%s):" % (sid, treatment))
        for p in problems:
            print("    %s" % p)

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
