"""Score the from-scratch candidates against the purpose inventory (2026-10-08).

Reads native/shared/build/scratch/<variant>-light-<tab>.png and .json (ScratchRenderTest, the
OnePlus 6T's geometry: 1080 x 2340 px, density 2.625). The ranks come from
design/scratch-2026-10-08/inventory.md; the assumptions are the constants below.

  order    Kendall's tau between purpose rank and vertical position, over the sections every
           candidate has (1 = the screen reads in purpose order)
  taps     taps to start-and-save each top action (typing the amount not counted), by action model
  reach    share of top-action controls on the first screen that sit in the thumb zone: the lower
           THUMB_ZONE of the content area (Hoober's natural one-handed reach on a 6.4" phone)
  covered  figures the floating add button covers on the first screen
  aligned / figures   the reference comparison's OCR measures (tools/refs_score.py)

    python tools/scratch_score.py
"""
import glob, json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
import refs_score

DIR = os.path.join(os.path.dirname(__file__), "..", "native", "shared", "build", "scratch")
D = 2.625
SCREEN_DP = 2340 / D
NAV_DP = 64                      # Sci-Fin's tab bar
THUMB_ZONE = 0.45                # the lower 45% of the content area

RANKS = {
    "budget": {"savings": 1, "Goals": 3, "Income": 5, "Recurring expenses": 6},
    "activity": {"savings": 1, "Accounts": 3, "Log": 4},
}
# taps to (log an expense, add to a goal), by model; today's screens put a + on Savings and on each goal
TAPS = {"today": (2, 2), "ledger-hero": (2, 2), "M1": (2, 3), "M2": (2, 2), "M3": (2, 2)}

def tau(order):
    n = len(order); c = d = 0
    for i in range(n):
        for j in range(i + 1, n):
            if order[i] < order[j]: c += 1
            elif order[i] > order[j]: d += 1
    return (c - d) / max(1, c + d)

def load(stem):
    return json.load(open(os.path.join(DIR, stem + ".json"), encoding="utf-8"))

def ys(probes, tab):
    out = {}
    for p in probes:
        n = p["name"]
        if n.startswith("section:") and n[8:] in RANKS[tab]: out.setdefault(n[8:], p["y"] / D)
        if n in ("figure", "el:savings"): out.setdefault("savings", p["y"] / D)
    if "savings" not in out:                                     # the ledger build reports no figure probe; Savings is always first
        out["savings"] = 0
    return out

def controls(probes, tab, model):
    """The first screen's top-action controls: the expense control and the goals' add buttons."""
    zone_top = (SCREEN_DP - NAV_DP) * (1 - THUMB_ZONE)
    first = lambda y: y < SCREEN_DP - NAV_DP
    fab = [p for p in probes if p["name"] == "el:fab"]
    rounds = [p for p in probes if p["name"].startswith("round:")]
    goals_y = next((p["y"] / D for p in probes if p["name"] == "section:Goals"), None)
    expense = fab[0]["y"] / D if fab else min((p["y"] / D for p in rounds if p["name"] in ("round:Quick-add",)), default=None)
    goal_btns = [p["y"] / D for p in (fab if model == "M1" else rounds)
                 if goals_y is not None and (model == "M1" or p["y"] / D > goals_y) and first(p["y"] / D)] if tab == "budget" else []
    ctl = ([expense] if expense is not None and first(expense) else []) + goal_btns
    return ctl, sum(1 for y in ctl if y >= zone_top)

def covered(probes):
    fab = [p for p in probes if p["name"] == "el:fab"]
    if not fab: return 0
    f = fab[0]
    figs = [p for p in probes if p["name"].startswith("el:") and p["name"] not in ("el:fab", "el:learn", "el:note") and not p["name"].startswith("el:add:")]
    return sum(1 for g in figs if g["x"] < f["x"] + f["w"] and g["x"] + g["w"] > f["x"] and g["y"] < f["y"] + f["h"] and g["y"] + g["h"] > f["y"])

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    variants = sorted({os.path.basename(p)[: -len("-light-budget.png")] for p in glob.glob(os.path.join(DIR, "*-light-budget.png"))})
    print(f"{'candidate':20} {'order B/A':>11} {'taps exp/goal':>14} {'reach':>6} {'covered':>8} {'aligned B/A':>12} {'figures B/A':>12}")
    rows = []
    for v in variants:
        model = v.split("-")[0] if v.startswith("M") else v
        r = {"name": v}
        o = []; reach = []; cov = 0; al = []; fg = []
        for tab in ("budget", "activity"):
            pr = load(f"{v}-light-{tab}")
            y = ys(pr, tab)
            seq = [RANKS[tab][k] for k, _ in sorted(y.items(), key=lambda kv: kv[1]) if k in RANKS[tab]]
            o.append(tau(seq))
            ctl, inzone = controls(pr, tab, model)
            if ctl: reach.append(inzone / len(ctl))
            cov += covered(pr)
            sc = refs_score.score(os.path.join(DIR, f"{v}-light-{tab}.png"))
            al.append(sc["alignment"]); fg.append(sc["facts"])
        r.update(order=o, taps=TAPS[model], reach=sum(reach) / len(reach) if reach else 0, covered=cov, aligned=al, figures=fg)
        rows.append(r)
        a = "/".join("-" if x is None else f"{x*100:.0f}%" for x in al)
        print(f"{v:20} {o[0]:5.2f}/{o[1]:4.2f} {r['taps'][0]:>8}/{r['taps'][1]} {r['reach']*100:5.0f}% {cov:8} {a:>12} {fg[0]:>7}/{fg[1]}")
    json.dump(rows, open(os.path.join(DIR, "scores.json"), "w"), indent=1)

if __name__ == "__main__":
    main()
