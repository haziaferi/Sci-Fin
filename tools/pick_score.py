"""The picked inversion fixes (2026-10-08): Savings scale + mid-tone fills + switch style, on M3, Goals second.

Renders: native/shared/build/scratchcalib/pick-* (ScratchCalibRenderTest with SCIFIN_PICK).
  visual   tools/function_score.py's axis, Budget and Activity, light and dark (as built: 0.463)
  bands    in the squint measure, light and dark, the owner's target: both + buttons below Savings
           and above Income; "strict" adds the purpose order Savings > floating + > goals > goal + > Income
  switch   more-important elements the switch outshines (0 is the target)
  glyphs   the + glyph on its mid-tone fill, 3:1 (WCAG 1.4.11) in all eight palettes; the fill is the
           Oklab blend Compose's lerp() makes, the glyph whichever of acc / onAcc reads better

    python tools/pick_score.py
"""
import glob, json, math, os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
import function_score as fs, inversion_score as iv, palette_derive as pd, scratch_score

DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "native", "shared", "build", "scratchcalib")
Q = fs.QUANT["squint"]

def oklab(h):
    L, C, H = pd.hex_to_oklch(h); return L, C * math.cos(math.radians(H)), C * math.sin(math.radians(H))

def blend(bg, acc, t):
    a, b = oklab(bg), oklab(acc)
    L, A, B = (x + (y - x) * t for x, y in zip(a, b))
    return pd.oklch_to_hex(L, math.hypot(A, B), math.degrees(math.atan2(B, A)) % 360)

def glyph_worst(share, pals):
    worst = (99, None)
    for name, p in pals.items():
        fill = blend("#" + p["bg"], "#" + p["acc"], share)
        v = max(pd.cr("#" + p["acc"], fill), pd.cr("#" + p["onAcc"], fill))
        if v < worst[0]: worst = (v, name)
    return worst

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    pals = iv.palettes()
    picks = sorted({re.match(r"pick-(k[\d.]+-f[\d.]+-g[\d.]+)-s\d", os.path.basename(p)).group(1) for p in glob.glob(os.path.join(DIR, "pick-*-budget.png"))})
    print(f"{'pick':22} {'switch':6} {'visual':>6}  {'bands (+ between Income and Savings)':>36}  {'strict order':>12}  {'above switch':>12}  {'covered':>7}  glyph 3:1 (fab / goal)")
    rows = []
    for pk in picks:
        k, f, g = (float(x[1:]) for x in pk.split("-"))
        act = {mode: fs.visual(os.path.join(DIR, f"pick-k{k}-f{f}-{mode}-activity.png"),
                                json.load(open(os.path.join(DIR, f"pick-k{k}-f{f}-{mode}-activity.json"))), "activity", "M3") for mode in ("light", "dark")}
        for sw in (0, 2, 4):
            taus = []; band = []; strict = []; above = []; cov = 0
            for mode in ("light", "dark"):
                stem = f"pick-{pk}-s{sw}-{mode}-budget"
                probes = json.load(open(os.path.join(DIR, stem + ".json")))
                tb, mb = fs.visual(os.path.join(DIR, stem + ".png"), probes, "budget", "M3")
                ta, ma = act[mode]
                taus += [sum(tb.values()) / 4, sum(ta.values()) / 4]
                sq = {e: v["squint"] for e, v in mb.items()}
                band.append(sq["income"] < sq["expense"] < sq["savings"] and sq["income"] < sq["goal_add"] < sq["savings"]
                            and ma["expense"]["squint"] < ma["savings"]["squint"])
                strict.append(sq["savings"] > sq["expense"] > sq["goals"] > sq["goal_add"] > sq["income"])
                above.append(sum(1 for e, r in fs.ELEMENTS["budget"].items() if r < 11 and 0 < sq[e] < sq["switch"] - Q))
                if mode == "light": cov = scratch_score.covered(probes)
            gf, gg = glyph_worst(f, pals), glyph_worst(g, pals)
            r = dict(pick=pk, switch=sw, visual=sum(taus) / len(taus), bands=all(band), strict=all(strict), above=max(above), covered=cov,
                     glyph_fab=gf, glyph_goal=gg)
            rows.append(r)
            print(f"{pk:22} s{sw:<5} {r['visual']:6.3f}  {'yes, light and dark' if r['bands'] else 'no':>36}  {'yes' if r['strict'] else 'no':>12}  {r['above']:12}  {cov:7}  "
                  f"{gf[0]:.2f} ({gf[1]}) / {gg[0]:.2f} ({gg[1]})")
    json.dump(rows, open(os.path.join(DIR, "picks.json"), "w", encoding="utf-8"), indent=1)

if __name__ == "__main__":
    main()
