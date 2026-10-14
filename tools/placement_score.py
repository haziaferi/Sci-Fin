"""The three placements still open after the rebuild (2026-10-09), scored. Renders: native/shared/build/placement
(PlacementRenderTest). Measured where a render can measure it; counted from the flow where it is a count.

Log income  A tap Savings -> choices   B a button on the Income row   C an Income direction in the floating +'s sheet
  taps       to start logging income, from Budget and from Activity (counted from each option's flow)
  parity     §3.6/§3.7: Activity's Savings carries the same affordances as Budget's
  refs       the reference apps' own entry points, observed on the 6T on 2026-10-09 (design/refs-apps/income-*):
             income as a choice inside the main add control 5 of 7 (Cashew, Money Manager, Wallet, My Expenses, Ivy),
             a separate visible income control 2 of 7 (Monefy's button, Goodbudget's Fill Envelopes), behind a tap on
             the balance 0 of 7
  visual     tools/function_score.py's visual axis on the phone renders (light and dark, Budget and Activity)
  edit       taps to the Savings full editor

Floating + at two columns  1 window's bottom-end   2 first column's bottom-end   3 top of the rail
  covered    figures (OCR money words) the + overlaps, desktop window, Budget and Activity, read off option 3's render
  nearest    the gap in dp between the + and the nearest figure (0 = touching)
  travel     the pointer's distance in dp from the Savings figure to the +
  phone      the same corner as on the phone (bottom-end)
  platform   Material 3 puts the FAB in the navigation rail when the layout has one

More  E expand in place   L open Learn
  reach      taps to the note's full text: E 1; L opens Learn's list, where no topic carries this note
  place      the person stays where they were

    python tools/placement_score.py
"""
import json, math, os, sys
sys.path.insert(0, os.path.dirname(__file__))
import function_score as fs, refs_score

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "native", "shared", "build", "placement")

def probes(stem): return json.load(open(os.path.join(D, stem + ".json")))

def log_income():
    rows = []
    flows = {  # (taps from Budget, taps from Activity, Activity parity, refs share, taps to the full editor)
        "A tap Savings, then Log income": (2, 2, True, 0 / 7, 2),
        "B button on the Income row": (1, None, False, 2 / 7, 1),
        "C Income inside the floating +": (2, 2, True, 5 / 7, 1),
    }
    for (label, (tb, ta, par, ref, ed)), key in zip(flows.items(), "ABC"):
        vis = []
        for mode in ("light", "dark"):
            for tab in ("budget", "activity"):
                stem = f"income{key}-phone-{mode}-{tab}"
                taus, _ = fs.visual(os.path.join(D, stem + ".png"), probes(stem), tab, "M3")
                vis.append(sum(taus.values()) / 4)
        rows.append(dict(option=label, taps_budget=tb, taps_activity=ta, parity=par, refs=ref, edit=ed, visual=sum(vis) / len(vis)))
    print("Log income")
    print(f"  {'option':34} {'taps B/A':>9} {'parity':>7} {'refs':>6} {'edit':>5} {'visual':>7}")
    for r in rows:
        print(f"  {r['option']:34} {r['taps_budget']:>4}/{r['taps_activity'] if r['taps_activity'] else '-':<4} {'yes' if r['parity'] else 'no':>7} {r['refs']*7:4.0f}/7 {r['edit']:5} {r['visual']:7.3f}")
    return rows

def desktop_add():
    rows = []
    for k, label in ((1, "1 bottom-end of the window"), (2, "2 bottom-end of the first column"), (3, "3 top of the rail")):
        figs = words = 0; travel = []; clearance = 1e9
        for tab in ("budget", "activity"):
            stem = f"add{k}-desktop-light-{tab}"
            pr = probes(stem)
            fab = next(p for p in pr if p["name"] == "el:fab")
            sav = next(p for p in pr if p["name"] == "el:savings")
            fx, fy, fw, fh = fab["x"], fab["y"], fab["w"], fab["h"]
            # a covered word cannot be read under the +: read the words off option 3, whose content area has no +
            ws, _ = refs_score.ocr_words(os.path.join(D, f"add3-desktop-light-{tab}.png"))
            for w in refs_score.merge_symbols(ws):
                if refs_score.is_money(w["t"]) and k != 3:
                    gx = max(w["x"] - (fx + fw), fx - (w["x"] + w["w"]), 0); gy = max(w["y"] - (fy + fh), fy - (w["y"] + w["h"]), 0)
                    clearance = min(clearance, math.hypot(gx, gy))
                if k != 3 and w["x"] < fx + fw and w["x"] + w["w"] > fx and w["y"] < fy + fh and w["y"] + w["h"] > fy:
                    words += 1; figs += refs_score.is_money(w["t"])
            travel.append(math.dist((sav["x"] + sav["w"] / 2, sav["y"] + sav["h"] / 2), (fx + fw / 2, fy + fh / 2)))
        rows.append(dict(option=label, covered_figures=figs, covered_words=words, clearance=None if k == 3 else clearance, travel=sum(travel) / 2, phone=k != 3, platform=k == 3))
    print("Floating + at two columns (desktop window 1100 x 760)")
    print(f"  {'option':34} {'figures covered':>15} {'nearest figure dp':>17} {'travel dp':>9} {'as on phone':>11} {'M3 rail':>7}")
    for r in rows:
        print(f"  {r['option']:34} {r['covered_figures']:15} {'- (in the rail)' if r['clearance'] is None else format(r['clearance'], '.0f'):>17} {r['travel']:9.0f} {'yes' if r['phone'] else 'no':>11} {'yes' if r['platform'] else 'no':>7}")
    return rows

def more():
    rows = [dict(option="E expand in place", reach="1 tap", place=True), dict(option="L open Learn", reach="not reached (no topic carries it)", place=False)]
    print("More")
    for r in rows: print(f"  {r['option']:34} full text: {r['reach']:34} stays in place: {'yes' if r['place'] else 'no'}")
    return rows

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    out = dict(log_income=log_income(), desktop_add=desktop_add(), more=more())
    json.dump(out, open(os.path.join(D, "placement-scores.json"), "w", encoding="utf-8"), indent=1)

if __name__ == "__main__":
    main()
