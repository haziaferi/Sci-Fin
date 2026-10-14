"""How much of each card is content and how much is padding (2026-10-07).

Reads the structure2 renders (density 2, 360dp wide). A block is a run of rows that differ from the
page ground; inside it, "ink" is any pixel far from the block's own fill (text, icons, bars). The
block's height against the span its ink covers says how much of the card is air.

    python tools/element_heights.py today-light-budget today-light-activity
"""
import os, sys
from collections import Counter
from PIL import Image

DIR = os.path.join(os.path.dirname(__file__), "..", "native", "shared", "build", "structure2")
PX = 2

def far(a, b, t): return max(abs(a[i] - b[i]) for i in range(3)) > t

def blocks(img):
    px = img.load(); w, h = img.size; bg = px[2, 300]
    rows = [any(far(px[x, y], bg, 3) for x in range(16, w - 16, 3)) for y in range(h - 140 * PX)]
    out, y = [], 0
    while y < len(rows):
        if rows[y]:
            y0 = y
            while y < len(rows) and rows[y]: y += 1
            out.append((y0, y))
        else: y += 1
    return bg, out

def ink_span(img, y0, y1):
    px = img.load(); w = img.size[0]
    fill = Counter(px[x, y] for y in range(y0, y1, 2) for x in range(16, w - 16, 6)).most_common(1)[0][0]
    ink = [y for y in range(y0, y1) if any(far(px[x, y], fill, 40) for x in range(24, w - 24, 2))]
    if not ink: return fill, 0, 0, 0
    lines, prev = 1, ink[0]
    for y in ink[1:]:
        if y - prev > 3 * PX: lines += 1
        prev = y
    return fill, ink[0] - y0, y1 - ink[-1], lines

if __name__ == "__main__":
    for stem in sys.argv[1:]:
        img = Image.open(os.path.join(DIR, stem + ".png")).convert("RGB")
        bg, bl = blocks(img)
        print(f"== {stem}")
        print(f"  {'y':>5} {'height':>6} {'ink':>5} {'air':>5}  lines  fill")
        tot_h = tot_air = 0
        for y0, y1 in bl:
            fill, top, bot, lines = ink_span(img, y0, y1)
            h = (y1 - y0) / PX
            filled = fill != bg
            if h < 4: continue
            air = (top + bot) / PX
            if filled: tot_h += h; tot_air += air
            print(f"  {y0/PX:5.0f} {h:6.1f} {h-air:5.1f} {air:5.1f}  {lines:5}  {'#%02x%02x%02x' % fill if filled else 'page'}")
        print(f"  filled blocks: {tot_air/tot_h*100:.0f}% of their height is top/bottom padding" if tot_h else "")
