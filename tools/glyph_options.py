"""Five ways out of the glyph/band conflict for pick C (2026-10-09), each rendered in all four themes, light and dark.

  1 theme icon3      palette-role glyphs (3:1 icon floor in Daylight and Dusk light), Savings in the accent
  2 theme            4.5:1 everywhere (searched near-black glyph in those two), Savings in the accent
  3 themetxt         4.5:1 everywhere, Savings in the text colour in light mode
  4 themebig         4.5:1 everywhere, Savings 1.6x in the three light themes with lighter accents
  5 themeacchlight   3:1 icon floor there, Savings in accH in light mode

Per option, over the 8 theme-mode pairs (squint measure, Budget and Activity):
  band     pairs where both + buttons sit below Savings and above Income
  margin   the smallest Savings - floating + gap (the measure ties anything under 5)
  floor    the smallest + glyph contrast (designkit, tools/plus_colours.py's colours)
  strict   pairs in purpose order Savings > floating + > goals > goal + > Income
  visual   function_score's visual axis, mean
Pareto front over band, margin, floor, strict and visual; ties broken by nothing.

    python tools/glyph_options.py
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
import function_score as fs, plus_colours as pc, inversion_score as iv, palette_derive as pd

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "native", "shared", "build", "scratchcalib")
OPTIONS = [("1 icon floor 3:1", "themeicon3", False), ("2 4.5:1, near-black", "theme", True),
           ("3 4.5:1, Savings in text", "themetxt", True), ("4 4.5:1, Savings 1.6x", "themebig", True),
           ("5 icon floor, Savings accH", "themeacchlight", False)]

def floor(searched):
    worst = 99
    for name, p in iv.palettes().items():
        for share in pc.SHARES.values():
            fill = pc.fill_for(p, share)
            g = pc.glyph_for(p, fill)[0] if searched else "#" + p[max(pc.ROLES, key=lambda r: pd.cr("#" + p[r], fill))]
            worst = min(worst, pd.cr(g, fill))
    return worst

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    rows = []
    for label, pre, searched in OPTIONS:
        band = strict = 0; margin = 99; vis = []; detail = []
        for th in ("swiss-blue", "daylight", "tallow-tome", "dusk"):
            for mode in ("light", "dark"):
                b = os.path.join(D, f"{pre}-{th}-{mode}-budget"); a = os.path.join(D, f"{pre}-{th}-{mode}-activity")
                tb, mb = fs.visual(b + ".png", json.load(open(b + ".json")), "budget", "M3")
                ta, ma = fs.visual(a + ".png", json.load(open(a + ".json")), "activity", "M3")
                sq = {e: v["squint"] for e, v in mb.items()}
                ok = sq["income"] < sq["expense"] < sq["savings"] and sq["income"] < sq["goal_add"] < sq["savings"] and ma["expense"]["squint"] < ma["savings"]["squint"]
                band += ok; strict += sq["savings"] > sq["expense"] > sq["goals"] > sq["goal_add"] > sq["income"]
                m = min(sq["savings"] - sq["expense"], ma["savings"]["squint"] - ma["expense"]["squint"])
                margin = min(margin, m); vis.append((sum(tb.values()) + sum(ta.values())) / 8)
                detail.append(f"{th[:5]}-{mode[0]} {m:+.1f}")
        rows.append(dict(option=label, band=band, margin=margin, floor=floor(searched), strict=strict, visual=sum(vis) / len(vis), detail=detail))
    keys = ["band", "margin", "floor", "strict", "visual"]
    for r in rows:
        r["dominated_by"] = [o["option"] for o in rows if o is not r and all(o[k] >= r[k] for k in keys) and any(o[k] > r[k] for k in keys)]
    print(f"{'option':28} {'band /8':>7} {'margin':>7} {'floor':>6} {'strict /8':>9} {'visual':>7}  pareto")
    for r in rows:
        print(f"{r['option']:28} {r['band']:7} {r['margin']:7.1f} {r['floor']:6.2f} {r['strict']:9} {r['visual']:7.3f}  {'front' if not r['dominated_by'] else 'dominated by ' + ', '.join(o.split()[0] for o in r['dominated_by'])}")
    print()
    for r in rows: print(f"{r['option']:28} Savings - floating +: " + ", ".join(r["detail"]))
    json.dump(rows, open(os.path.join(D, "glyph-options.json"), "w", encoding="utf-8"), indent=1)

if __name__ == "__main__":
    main()
