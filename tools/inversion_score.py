"""The shared-inversion fixes (2026-10-08), on M3 with Goals second.

Renders: native/shared/build/scratchfix (ScratchFixRenderTest), g<goal +>-s<switch>-f<floating +>.
  goal +    0 as built (surf2 circle, txt2 glyph)   1 surf circle, accent glyph   2 outlined, accent glyph   3 accent fill
  switch    0 as built (track, bold body labels)    1 text tabs + accent underline   2 caption labels in the track   3 both
  float +   0 as built (accent, 56dp)               1 tonal (surf2, accent glyph)    2 accent, 48dp   3 tonal, 48dp

Scored on:
  visual    tools/function_score.py's visual axis (size, weight, colour, squint), Budget and Activity, light and dark
  the three targets, in the squint measure (the one that told the models apart), light and dark:
    goal+   the goals' + at least as salient as Income and "New goal"         margin = goal_add - max(income, new_goal)
    switch  no more-important element less salient than the switch            count of such elements
    float+  the Savings figure more salient than the floating +                margin = savings - floating +
  contrast  every theme, light and dark, from Tokens.kt: glyphs and the underline 3:1 on their fill (WCAG 1.4.11),
            switch labels 4.5:1 on theirs (1.4.3)
  covered   figures under the floating + (scratch_score.covered)

    python tools/inversion_score.py
"""
import itertools, json, os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
import function_score as fs, scratch_score

HERE = os.path.dirname(os.path.abspath(__file__))
DIR = os.path.join(HERE, "..", "native", "shared", "build", "scratchfix")
TOKENS = os.path.join(HERE, "..", "native", "shared", "src", "commonMain", "kotlin", "app", "scifin", "ui", "Tokens.kt")
FIELDS = ["bg", "surf", "surf2", "bord", "bordStrong", "txt", "txt2", "txt3", "acc", "accH", "onAcc"]
Q = fs.QUANT["squint"]

def palettes():
    out = {}
    for name, args in re.findall(r"val (\w+(?:Light|Dark)) = Palette\(([^)]*\)[^\n]*)\)", open(TOKENS, encoding="utf-8").read()):
        out[name] = dict(zip(FIELDS, re.findall(r'hex\("([0-9A-Fa-f]{6})"\)', args)))
    return out

def lum(h):
    def ch(c):
        c = int(c, 16) / 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (ch(h[i:i + 2]) for i in (0, 2, 4))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b

def cr(a, b):
    la, lb = sorted((lum(a), lum(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)

def pairs(g, s, f):
    """(what, fg role, bg role, floor) for this combination."""
    out = [{0: ("goal + glyph", "txt2", "surf2"), 1: ("goal + glyph", "acc", "surf"), 2: ("goal + glyph", "acc", "surf2"), 3: ("goal + glyph", "onAcc", "acc")}[g] + (3.0,)]
    if s in (0, 2): out += [("switch, selected", "txt", "surf", 4.5), ("switch, other", "txt2", "surf2", 4.5)]
    else: out += [("switch, selected", "txt", "bg", 4.5), ("switch, other", "txt2", "bg", 4.5), ("switch underline", "acc", "bg", 3.0)]
    out += [("floating + glyph", "acc", "surf2", 3.0) if f in (1, 3) else ("floating + glyph", "onAcc", "acc", 3.0)]
    return out

def contrast(g, s, f, pals):
    worst = {}
    for pname, pal in pals.items():
        for what, fg, bg, floor in pairs(g, s, f):
            v = cr(pal[fg], pal[bg])
            if what not in worst or v < worst[what][0]: worst[what] = (v, floor, pname)
    return worst

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    pals = palettes(); assert len(pals) == 8, pals.keys()
    act = {}
    for f in range(4):
        for mode in ("light", "dark"):
            stem = f"f{f}-{mode}-activity"
            act[(f, mode)] = fs.visual(os.path.join(DIR, stem + ".png"), json.load(open(os.path.join(DIR, stem + ".json"))), "activity", "M3")
    rows = []
    for g, s, f in itertools.product(range(4), range(4), range(4)):
        taus = []; t1 = []; t2 = []; t3 = []; cov = 0
        for mode in ("light", "dark"):
            stem = f"g{g}-s{s}-f{f}-{mode}-budget"
            probes = json.load(open(os.path.join(DIR, stem + ".json")))
            tb, mb = fs.visual(os.path.join(DIR, stem + ".png"), probes, "budget", "M3")
            ta, ma = act[(f, mode)]
            taus += [sum(tb.values()) / 4, sum(ta.values()) / 4]
            sq = {e: m["squint"] for e, m in mb.items()}
            t1.append(sq["goal_add"] - max(sq["income"], sq["new_goal"]))
            t2.append(sum(1 for e, r in fs.ELEMENTS["budget"].items() if r < 11 and 0 < sq[e] < sq["switch"] - Q))
            t3.append(min(sq["savings"] - sq["expense"], ma["savings"]["squint"] - ma["expense"]["squint"]))
            if mode == "light": cov = scratch_score.covered(probes)
        con = contrast(g, s, f, pals)
        rows.append(dict(name=f"g{g}-s{s}-f{f}", g=g, s=s, f=f, visual=sum(taus) / len(taus),
                         goal_margin=min(t1), switch_above=max(t2), float_margin=min(t3), covered=cov,
                         contrast_ok=all(v >= fl for v, fl, _ in con.values()),
                         contrast={k: (round(v, 2), fl, p) for k, (v, fl, p) in con.items()}))
    rows.sort(key=lambda r: -r["visual"])
    ok = lambda r: r["goal_margin"] >= 0 and r["switch_above"] == 0 and r["float_margin"] >= Q and r["contrast_ok"]
    print(f"{'combo':10} {'visual':>6} {'goal+ margin':>12} {'above switch':>12} {'float margin':>12} {'covered':>7} contrast  all targets")
    for r in rows:
        print(f"{r['name']:10} {r['visual']:6.3f} {r['goal_margin']:12.1f} {r['switch_above']:12} {r['float_margin']:12.1f} {r['covered']:7} {'ok' if r['contrast_ok'] else 'FAIL':8}  {'yes' if ok(r) else ''}")
    print("\ncontrast, worst theme per pair:")
    seen = set()
    for r in rows:
        for k, (v, fl, p) in r["contrast"].items():
            key = (k, r["g"] if "goal" in k else r["s"] if "switch" in k else r["f"])
            if key in seen: continue
            seen.add(key); print(f"  {k:18} option {key[1]}  {v:5.2f} (floor {fl}) worst in {p}")
    json.dump(rows, open(os.path.join(DIR, "inversion.json"), "w", encoding="utf-8"), indent=1)

if __name__ == "__main__":
    main()
