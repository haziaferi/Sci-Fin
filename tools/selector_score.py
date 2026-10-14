"""The entry sheet's target, two ways (owner, 2026-10-09: "goals and categories can be numerous ... a selector can be put
in front of the question: Savings/Goals/Categories/Accounts. The rest of the sentence follows and 'to X' then aims at
only the chips within that group").

  P  today's prototype: the target word opens one picker of every editable item, grouped, the pressed kind's group first
  G  a four-way selector (Savings | Goals | Categories | Accounts) above the sentence; the target word's picker holds only
     that group's chips; choosing a group opens its picker at once (Savings has one item, so nothing opens)

Measured on the prototype's own geometry: the sheet is 332 px wide inside its padding, a chip is 14 px type (7.4 px per
character, measured on the render) plus 24 px padding and 6 px gap, 48 px a chip row, 26 px a group heading, and the
picker area holds 470 px before it scrolls (a 740 px phone, the sheet at 94%, minus its foot). Two sets: the seed
(4 goals, 5 categories, 2 accounts) and a numerous one (12, 20, 3).

Per task: taps after the sheet opens, scrolls, and chips in view when choosing (more chips, longer search; Hick).
Rest height: the sheet before anything is tapped (the selector costs one 48 px row).

    python tools/selector_score.py
"""
import math
import sys

W, ROW, HEAD, AREA = 332, 48, 26, 470
SEED = {"goal": ["Emergency fund", "Trip to Lisbon", "Someday fund", "Holidays"],
        "cat": ["Groceries", "Dining out", "Transport", "Entertainment", "Holidays"],
        "acct": ["Credit Card", "Car Loan"]}
MANY = {"goal": SEED["goal"] + ["New laptop", "Wedding", "Car repairs", "Christmas", "Dentist", "Moving house", "Bike", "Gifts"],
        "cat": SEED["cat"] + ["Pharmacy", "Clothes", "Pets", "Hobbies", "Books", "Gym", "Haircut", "Coffee", "Takeaway",
                              "Taxi", "Parking", "Fuel", "Household", "Kids", "Charity"],
        "acct": SEED["acct"] + ["Store card"]}

def rows(names):
    r, x = 1, 0
    for n in names:
        w = len(n) * 7.4 + 24 + 6
        if x + w > W: r, x = r + 1, 0
        x += w
    return r

def picker_h(groups):  # groups: list of name lists
    return sum(HEAD + rows(g) * ROW for g in groups)

def tasks(S):
    savings = ["Savings"]
    allg = [savings, S["goal"], S["cat"], S["acct"]]
    out = []
    # (task, from kind) -> P taps, P scroll, P chips in view; G taps, G scroll, G chips
    p_h = picker_h(allg)
    for task, frm, to in [("same target as opened", "goal", None), ("another goal", "goal", "goal"),
                          ("a category, opened on a goal", "goal", "cat"), ("an account, opened on a goal", "goal", "acct"),
                          ("Savings, opened on a category", "cat", "savings")]:
        if to is None:
            out.append((task, 0, 0, 0, 0, 0, 0)); continue
        # P: tap the target word, (scroll if the wanted group is below the fold), tap the chip
        order = [frm] + [k for k in ("savings", "goal", "cat", "acct") if k != frm]
        top = sum(HEAD + rows(([*savings] if k == "savings" else S[k])) * ROW for k in order[:order.index(to)])
        scroll = 1 if top + HEAD + ROW > AREA else 0
        p = (2 + scroll, scroll, sum(len(g) for g in allg))
        # G: same group -> target word + chip; other group -> selector (opens the picker) + chip; Savings -> selector only
        if to == frm: g = (2, 0, len(S[to]))
        elif to == "savings": g = (1, 0, 0)
        else: g = (2, 1 if HEAD + rows(S[to]) * ROW > AREA else 0, len(S[to]))
        out.append((task, *p, *g))
    return out, p_h, max(picker_h([S[k]]) for k in ("goal", "cat", "acct"))

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    for label, S in (("seed (4 goals, 5 categories, 2 accounts)", SEED), ("numerous (12 goals, 20 categories, 3 accounts)", MANY)):
        rows_, p_h, g_h = tasks(S)
        print(label)
        print(f"  {'task':34} {'P taps':>6} {'scroll':>6} {'chips':>5}   {'G taps':>6} {'scroll':>6} {'chips':>5}")
        tot = [0] * 6
        for r in rows_:
            print(f"  {r[0]:34} {r[1]:6} {r[2]:6} {r[3]:5}   {r[4]:6} {r[5]:6} {r[6]:5}")
            tot = [a + b for a, b in zip(tot, r[1:])]
        hick = lambda n: 0.15 * math.log2(n + 1) if n else 0
        ph = sum(hick(r[3]) for r in rows_); gh = sum(hick(r[6]) for r in rows_)
        print(f"  {'total':34} {tot[0]:6} {tot[1]:6} {'':5}   {tot[3]:6} {tot[4]:6}")
        print(f"  choosing time (Hick, 0.15 s per bit): P {ph:.2f} s   G {gh:.2f} s")
        print(f"  picker height when open: P {p_h} px (area {AREA})   G at most {g_h} px")
        print(f"  sheet at rest: P 0 px extra   G +{ROW} px (the selector row)\n")

if __name__ == "__main__":
    main()
