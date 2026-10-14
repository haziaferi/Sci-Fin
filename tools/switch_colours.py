"""The Envelopes/Categories switch's weak colours, re-derived (2026-10-08, color-optimization-core).

For each of the eight palettes, enumerate a track colour T and a selected-chip colour C at the hue of
the palette's surf2, minimising how far the switch stands from the ground (CIEDE2000(T, bg) + CIEDE2000(C, bg),
the two halves of its area), subject to:
  - the chip no closer to the track than it is now (CIEDE2000(C, T) >= the palette's current surf vs surf2)
  - the unselected label (txt2) 4.5:1 on T, the selected label (txt) 4.5:1 on C
  - on a dark ground, the chip lighter than the ground
Contrast and CIEDE2000 are designkit's (palette_derive imports them). The space is small, so it is
enumerated, not searched.

    python tools/switch_colours.py           # the table
    python tools/switch_colours.py --kotlin  # SWITCH_CHIPS for ui/Derived.kt
"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import palette_derive as pd, inversion_score as iv

def grid(bg_hex, hue):
    L0 = pd.hex_to_oklch(bg_hex)[0]
    out = set()
    for i in range(-40, 41):
        for j in range(0, 11):
            out.add(pd.oklch_to_hex(min(1, max(0, L0 + i * 0.004)), j * 0.004, hue))
    return sorted(out)

def solve(p):
    h = lambda r: "#" + p[r]
    bg, hue = h("bg"), pd.hex_to_oklch(h("surf2"))[2]
    sep0 = pd.de(h("surf"), h("surf2"))
    cands = grid(bg, hue) + [bg]
    dist = {c: pd.de(c, bg) for c in cands}
    tracks = [c for c in cands if pd.cr(h("txt2"), c) >= 4.5]
    dark = pd.hex_to_oklch(bg)[0] < 0.5
    # on a dark ground a raised chip is lighter than the ground (a darker one reads as a hole)
    chips = sorted((c for c in cands if pd.cr(h("txt"), c) >= 4.5 and (not dark or pd.hex_to_oklch(c)[0] > pd.hex_to_oklch(bg)[0])), key=dist.get)
    best = None
    for t in sorted(tracks, key=dist.get):
        if best and dist[t] >= best[0]: break
        for c in chips:
            if best and dist[t] + dist[c] >= best[0]: break
            if pd.de(c, t) >= sep0:
                best = (dist[t] + dist[c], t, c); break
    return dict(now=(dist.get(h("surf2"), pd.de(h("surf2"), bg)) + pd.de(h("surf"), bg), h("surf2"), h("surf")), new=best, sep0=sep0,
                txt2=pd.cr(h("txt2"), best[1]), txt=pd.cr(h("txt"), best[2]), sep=pd.de(best[2], best[1]))

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    if "--kotlin" in sys.argv:
        for name, p in iv.palettes().items():
            print(f"    Palettes.{name} to Color(0xFF{solve(p)['new'][2][1:].upper()}),")
        return
    print(f"{'palette':16} {'now: track chip  salience':>34}   {'new: track chip  salience':>34}  chip~track (floor)  txt2/track  txt/chip")
    for name, p in iv.palettes().items():
        r = solve(p); n = r["now"]; b = r["new"]
        print(f"{name:16} {n[1]:>9} {n[2]:>9} {n[0]:8.1f}       {b[1]:>9} {b[2]:>9} {b[0]:8.1f}   {r['sep']:5.1f} ({r['sep0']:.1f})      {r['txt2']:5.2f}      {r['txt']:5.2f}")

if __name__ == "__main__":
    main()
