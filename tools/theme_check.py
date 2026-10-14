"""Pick C in every theme and mode (2026-10-09): does the salience band hold everywhere, not only in Swiss Blue?

Renders: native/shared/build/scratchcalib/theme-<token>-<mode>-<tab> (ScratchCalibRenderTest, SCIFIN_THEMES=1).
  band     both + buttons below Savings and above Income (squint), and the floating + below Savings on Activity
  strict   Savings > floating + > goals > goal + > Income
  above    more-important elements the switch outshines

    python tools/theme_check.py [prefix]   # theme (default), themeicon3, themeacch
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
import function_score as fs, scratch_score

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "native", "shared", "build", "scratchcalib")
Q = fs.QUANT["squint"]

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    print(f"{'theme':12} {'mode':5} {'visual':>6}  savings float+ goal+ goals income switch | act savings float+  band  strict  above covered")
    allok = True
    pre = sys.argv[1] if len(sys.argv) > 1 else "theme"
    for th in ("swiss-blue", "daylight", "tallow-tome", "dusk"):
        for mode in ("light", "dark"):
            b = os.path.join(D, f"{pre}-{th}-{mode}-budget"); a = os.path.join(D, f"{pre}-{th}-{mode}-activity")
            pb = json.load(open(b + ".json"))
            tb, mb = fs.visual(b + ".png", pb, "budget", "M3"); ta, ma = fs.visual(a + ".png", json.load(open(a + ".json")), "activity", "M3")
            sq = {e: v["squint"] for e, v in mb.items()}
            band = sq["income"] < sq["expense"] < sq["savings"] and sq["income"] < sq["goal_add"] < sq["savings"] and ma["expense"]["squint"] < ma["savings"]["squint"]
            strict = sq["savings"] > sq["expense"] > sq["goals"] > sq["goal_add"] > sq["income"]
            above = sum(1 for e, r in fs.ELEMENTS["budget"].items() if r < 11 and 0 < sq[e] < sq["switch"] - Q)
            allok &= band
            vis = (sum(tb.values()) + sum(ta.values())) / 8
            print(f"{th:12} {mode:5} {vis:6.3f}  {sq['savings']:7.1f} {sq['expense']:6.1f} {sq['goal_add']:5.1f} {sq['goals']:5.1f} {sq['income']:6.1f} {sq['switch']:6.1f} |"
                  f" {ma['savings']['squint']:11.1f} {ma['expense']['squint']:6.1f}  {'yes' if band else 'NO':4}  {'yes' if strict else 'no':6}  {above:5} {scratch_score.covered(pb):3}")
    print("band met in every theme and mode:", allok)

if __name__ == "__main__":
    main()
