"""Improved variants of the three open placements (2026-10-09), against the originals; dominance computed exactly.
Renders: native/shared/build/placement (PlacementRenderTest). Counts are from each option's flow; the rest is measured.

    python tools/improved_score.py
"""
import json, math, os, sys
sys.path.insert(0, os.path.dirname(__file__))
import function_score as fs, refs_score

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "native", "shared", "build", "placement")
INF = 9

def probes(stem): return json.load(open(os.path.join(D, stem + ".json")))

def visual(key):
    v = []
    for mode in ("light", "dark"):
        for tab in ("budget", "activity"):
            stem = f"income{key}-phone-{mode}-{tab}"
            taus, _ = fs.visual(os.path.join(D, stem + ".png"), probes(stem), tab, "M3"); v.append(sum(taus.values()) / 4)
    return sum(v) / len(v)

def front(rows, keys):
    """keys: [(field, +1 more is better | -1 less is better)]"""
    for a in rows:
        a["dominated_by"] = [b["option"] for b in rows if b is not a and all(s * b[k] >= s * a[k] for k, s in keys) and any(s * b[k] > s * a[k] for k, s in keys)]

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    # ---- Log income: (taps on an overdue day, Budget / Activity; taps otherwise, Budget / Activity; parity; refs; editor taps; stable default)
    li = [
        ("A tap Savings -> choices", "A", 2, 2, 2, 2, 0 / 7, 2, True),
        ("B button on the Income row", "B", 1, INF, 1, INF, 2 / 7, 1, True),
        ("C Income inside the +", "C", 2, 2, 2, 2, 5 / 7, 1, True),
        ("N  C + action on the overdue note", "N", 1, 2, 2, 2, 5 / 7, 1, True),
        ("P  C, + opens on Income when overdue", "P", 1, 1, 2, 2, 5 / 7, 1, False),
    ]
    rows = [dict(option=o, overdue=(b + a) / 2, normal=(nb + na) / 2, refs=r, edit=e, stable=int(st), visual=visual(k)) for o, k, b, a, nb, na, r, e, st in li]
    front(rows, [("overdue", -1), ("normal", -1), ("refs", 1), ("edit", -1), ("stable", 1), ("visual", 1)])
    print("Log income  (taps averaged over Budget and Activity; 9 = not available there)")
    print(f"  {'option':38} {'overdue':>7} {'normal':>6} {'refs':>5} {'edit':>4} {'stable default':>14} {'visual':>7}  front")
    for r in rows:
        print(f"  {r['option']:38} {r['overdue']:7.1f} {r['normal']:6.1f} {r['refs']*7:3.0f}/7 {r['edit']:4} {'yes' if r['stable'] else 'no':>14} {r['visual']:7.3f}  {'yes' if not r['dominated_by'] else 'dominated by ' + ', '.join(x.split()[0] for x in r['dominated_by'])}")
    # ---- Floating + at two columns
    dk = []
    for k, label in ((1, "1 window corner"), (2, "2 first column corner"), (3, "3 rail"), (4, "4 rail, labelled")):
        travel = []
        for tab in ("budget", "activity"):
            pr = probes(f"add{k}-desktop-light-{tab}")
            f = next(p for p in pr if p["name"] == "el:fab"); s = next(p for p in pr if p["name"] == "el:savings")
            travel.append(math.dist((s["x"] + s["w"] / 2, s["y"] + s["h"] / 2), (f["x"] + f["w"] / 2, f["y"] + f["h"] / 2)))
        dk.append(dict(option=label, over_content=int(k <= 2), travel=sum(travel) / 2, phone_corner=int(k <= 2), rail=int(k >= 3), named=int(k == 4)))
    front(dk, [("over_content", -1), ("travel", -1), ("phone_corner", 1), ("rail", 1), ("named", 1)])
    print("Floating + at two columns")
    print(f"  {'option':38} {'floats over content':>19} {'travel dp':>9} {'phone corner':>12} {'M3 rail':>7} {'label':>5}  front")
    for r in dk:
        print(f"  {r['option']:38} {'yes' if r['over_content'] else 'no':>19} {r['travel']:9.0f} {'yes' if r['phone_corner'] else 'no':>12} {'yes' if r['rail'] else 'no':>7} {'yes' if r['named'] else 'no':>5}  {'yes' if not r['dominated_by'] else 'dominated by ' + ', '.join(x.split()[0] for x in r['dominated_by'])}")
    # ---- More: target width measured (E: the word's OCR box; R: the note line's probe)
    ws, _ = refs_score.ocr_words(os.path.join(D, "moreE-phone-light-activity.png"))
    more_w = next(w["w"] for w in ws if w["t"] == "More") / 2.625
    note = next(p for p in probes("moreR-phone-light-activity") if p["name"] == "el:note")
    mo = [dict(option="E expand, the word is the target", target=min(more_w, 48) / 48, taps=1, in_place=1, same_pattern=0),
          dict(option="L open Learn", target=min(more_w, 48) / 48, taps=INF, in_place=0, same_pattern=0),
          dict(option="R expand, the whole line is the target", target=min(note["w"] / 2.625, note["h"] / 2.625, 48) / 48, taps=1, in_place=1, same_pattern=1)]
    front(mo, [("target", 1), ("taps", -1), ("in_place", 1), ("same_pattern", 1)])
    print(f"More  (target = smaller side / 48dp; E's word measures {more_w:.0f}dp wide; R's line {note['w']/2.625:.0f} x {note['h']/2.625:.0f}dp)")
    for r in mo:
        print(f"  {r['option']:38} target {r['target']:.2f}  taps {r['taps'] if r['taps'] < INF else 'never'}  in place {'yes' if r['in_place'] else 'no'}  disclosure pattern {'yes' if r['same_pattern'] else 'no'}  {'front' if not r['dominated_by'] else 'dominated by ' + ', '.join(x.split()[0] for x in r['dominated_by'])}")
    json.dump(dict(log_income=rows, desktop=dk, more=mo), open(os.path.join(D, "improved-scores.json"), "w", encoding="utf-8"), indent=1)

if __name__ == "__main__":
    main()
