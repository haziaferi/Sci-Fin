"""The desktop placement of Add, scored with desktop parameters (2026-10-09, owner: "the desktop navigation does
not have the same boundaries as a phone screen"). Renders: native/shared/build/placement/add<k>-desktop-light-*
(PlacementRenderTest, 1100 x 760 window, density 1, so px = dp).

  first target  Fitts index of difficulty, log2(D/W + 1) bits, from the Savings figure (where the eye starts)
                to the first thing the pointer must hit; W is the target's smaller side. No constants.
  KLM           the keystroke-level model's operators for "log EUR 12.50 at Coffee" from the resting screen, hand on
                the mouse, and its time with Card, Moran & Newell's published values (K 0.28 s average typist,
                P 1.1 s, click 0.2 s, H 0.4 s, M 1.35 s). The quick-add sheet does not focus its amount field and
                has no Enter-to-confirm today, so the sheet flows point at both.
  added         always-visible controls the option adds to the first screen (Budget + Activity), counted from the
                renders' probes and the layout
  over content  whether it floats over the content area
  desktop refs  desktop apps observed (2026-10-09 web check): register entry rows -- GnuCash's blank row, YNAB's
                Add Transaction on the register; a header + -- Firefly III; no floating or rail button seen

    python tools/desktop_score.py
"""
import json, math, os, sys

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "native", "shared", "build", "placement")
K, P, B, H, M = 0.28, 1.1, 0.2, 0.4, 1.35       # Card, Moran & Newell (1983); B is a full click (press + release)
SHEET = dict(M=1, P=3, B=3, H=2, K=12)           # point+click Add, point+click Amount, type 12.50 Tab Coffee, home, point+click Confirm
INLINE = dict(M=1, P=2, B=2, H=2, K=12)          # point+click Amount, type, home, point+click Add

def klm(o): return o["M"] * M + o["P"] * P + o["B"] * B + o["H"] * H + o["K"] * K

def probes(stem): return json.load(open(os.path.join(D, stem + ".json")))

def centre(p): return (p["x"] + p["w"] / 2, p["y"] + p["h"] / 2)

def fitts(k, tab, target=None):
    pr = probes(f"add{k}-desktop-light-{tab}")
    s = next(p for p in pr if p["name"] == "el:savings")
    t = target or next(p for p in pr if p["name"] == "el:fab")
    return math.log2(math.dist(centre(s), centre(t)) / min(t["w"], t["h"]) + 1)

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    # I's first target is the inline Amount field, anchored on its OCR-read label (x 725, y 73) in the Activity window
    amount_field = dict(x=725, y=88, w=100, h=32)
    rows = []
    for k, label, flow, added, over, refs in (
        (1, "1 floating, window corner", SHEET, 2, True, "none"),
        (2, "2 floating, first column corner", SHEET, 2, True, "none"),
        (3, "3 rail top", SHEET, 2, False, "Firefly III (header +)"),
        (4, "4 rail top, labelled", SHEET, 2, False, "Firefly III (header +)"),
        (5, "R Add on Savings, + on accounts", SHEET, 2 + 2, False, "YNAB (Add on the register)"),
        (6, "I = R + inline row in the Activity log", INLINE, 2 + 2 + 4, False, "GnuCash, YNAB (register entry)"),
    ):
        ids = [fitts(k, "budget"), fitts(k, "activity", amount_field if k == 6 else None)]
        rows.append(dict(option=label, id=sum(ids) / 2, klm=klm(flow), added=added, over=int(over), refs=refs))
    keys = [("id", -1), ("klm", -1), ("added", -1), ("over", -1)]
    for a in rows:
        a["dominated_by"] = [b["option"].split()[0] for b in rows if b is not a and all(s * b[f] >= s * a[f] for f, s in keys) and any(s * b[f] > s * a[f] for f, s in keys)]
    print(f"{'option':40} {'first target bits':>17} {'KLM s':>6} {'added':>5} {'over content':>12}  {'desktop refs':32} front")
    for r in rows:
        print(f"{r['option']:40} {r['id']:17.2f} {r['klm']:6.2f} {r['added']:5} {'yes' if r['over'] else 'no':>12}  {r['refs']:32} {'yes' if not r['dominated_by'] else 'dominated by ' + ', '.join(r['dominated_by'])}")
    kb = dict(M=1, P=1, B=1, H=1, K=13)
    print(f"\nKeyboard completion, additive to any sheet option: amount focused on open + Enter confirms -> {klm(kb):.2f} s;"
          f" with a shortcut opening the sheet (H, Ctrl+N, 12.50 Tab Coffee Enter) -> {klm(dict(M=1, P=0, B=0, H=1, K=15)):.2f} s")
    json.dump(rows, open(os.path.join(D, "desktop-scores.json"), "w", encoding="utf-8"), indent=1)

if __name__ == "__main__":
    main()
