"""One entry for every editable element (owner, 2026-10-09): how a typed line finds its target, and whether the line is
needed at all once each + opens the sentence (W) already pointed at its own kind.

Part 1, names: five ways for the capture line to find the target, each a reference matcher run over the same cases,
built from the prototype seed's real names plus the owner's "Holidays". The names already collide the ways that matter:
"Trip to Lisbon" holds "to", "Dining out" holds "out", "Car Loan" sits beside "Car insurance" and "Car loan payment",
and a Goal and a Category can share a name ("Holidays" below). W, the sentence, never reads a name: its target word is a
picker, so it is scored as the control.

  C1 bare names: the longest whole-word name in the line wins; two kinds with that name, or none, asks
  C2 kind nouns: "goal Holidays", "category Entertainment", "account Credit Card"; a bare name asks
  C3 marks: Factotum-style shorthands before the name: ^ goal, # category, @ account (a legend lists them)
  C4 C1, plus names unique across kinds (the app refuses a second "Holidays" when it is created)
  C5 C1, plus name chips above the keyboard after "to"/"on"/"for": a tap binds the name, so a part word is enough
  W  the sentence: the + pressed sets the kind, the target word opens the grouped picker

Measured per option over the cases: wrong writes (a target the person did not mean, must be 0: section 4.3), asks
(one more step), phone effort (keys and taps), and how many rules must be known before the line works (hidden grammar).

Part 2, arrangements: the line on every screen plus W on each +, against W alone (the owner's point 6), over five tasks,
each from the screen that holds its target and from a screen that does not. Keystroke-level model on a desktop (Card,
Moran & Newell: K 0.28, P 1.1, click 0.2, H 0.4, M 1.35); keys and taps on a phone.

    python tools/entry_kinds_score.py
"""
import re
import sys

GOALS = ["Emergency fund", "Trip to Lisbon", "Someday fund", "Holidays"]
CATS = ["Groceries", "Dining out", "Transport", "Entertainment", "Holidays"]       # a Category named like a Goal
ACCTS = ["Credit Card", "Car Loan"]
RECUR = ["Rent", "Phone bill", "Streaming service", "Car insurance", "Pest control", "Car loan payment"]
ITEMS = [("goal", n) for n in GOALS] + [("cat", n) for n in CATS] + [("acct", n) for n in ACCTS]
KIND_NOUN = {"goal": "goal", "cat": "category", "acct": "account"}
MARK = {"goal": "^", "cat": "#", "acct": "@"}

# line, the target meant (None = Savings), the part word a person types before tapping a chip (C5)
CASES = [
    ("spent 30 on Entertainment", ("cat", "Entertainment"), "ent"),
    ("add 50 to Trip to Lisbon", ("goal", "Trip to Lisbon"), "tri"),
    ("add 50 to Holidays", ("goal", "Holidays"), "hol"),          # a Goal and a Category share it
    ("spent 40 on Holidays", ("cat", "Holidays"), "hol"),
    ("spent 22 on Dining out", ("cat", "Dining out"), "din"),
    ("charge 40 on Credit Card", ("acct", "Credit Card"), "cre"),
    ("paid 260 on Car Loan", ("acct", "Car Loan"), "car"),
    ("spent 12 on groceries", ("cat", "Groceries"), "gro"),
    ("add 100 to Emergency fund", ("goal", "Emergency fund"), "eme"),
    ("spent 30 on fun", ("cat", "Entertainment"), "ent"),          # no such name: never a guess (4.3)
    ("spent 9 on car", ("acct", "Car Loan"), "car"),               # a part name: three items start with "car"
    ("coffee 3.50", ("cat", "Dining out"), "din"),             # no Money out on Savings (owner, 2026-10-09): it asks
    ("received 1450", None, None),
    ("got 20 from Anna", None, None),                           # money in still goes to Savings
]

def norm(s): return " " + re.sub(r"\s+", " ", s.lower()) + " "

def bare(line, items=ITEMS):
    """Longest whole-word name wins; a tie across items asks; a preposition followed by no name asks."""
    hits = [(k, n) for k, n in items if norm(n) in norm(line)]
    if hits:
        top = max(len(n) for _, n in hits)
        best = [h for h in hits if len(h[1]) == top]
        if len(best) > 1:   # the word before the name is grammar: only goals take "from", Categories and Accounts "on"
            pre = norm(line).split(norm(best[0][1]))[0].split()[-1:]
            fits = {"from": ["goal"], "on": ["cat", "acct"], "to": ["goal", "acct"]}.get(pre[0] if pre else "")
            if fits and [h for h in best if h[0] in fits]: best = [h for h in best if h[0] in fits]
        return ("ask", None) if len(best) > 1 else ("write", best[0])
    if re.search(r"\b(on|to|for|into)\s+\w", line.lower()):
        return ("ask", None)
    # owner, 2026-10-09: Savings only takes money in, so a line with no target writes only when it says money came in
    return ("write", None) if re.match(r"(received|got|in)\b", line.lower()) else ("ask", None)

def nouns(line):
    for k, n in ITEMS:
        if norm(KIND_NOUN[k] + " " + n) in norm(line): return ("write", (k, n))
    return bare(line, items=[]) if not any(norm(n) in norm(line) for _, n in ITEMS) else ("ask", None)

def marks(line):
    for k, n in sorted(ITEMS, key=lambda x: -len(x[1])):
        if (MARK[k] + n).lower() in line.lower(): return ("write", (k, n))
    return bare(line, items=[]) if not any(norm(n) in norm(line) for _, n in ITEMS) else ("ask", None)

UNIQUE = [i for i in ITEMS if i != ("cat", "Holidays")]   # C4: the second Holidays was refused at creation

def written(option, line, meant):
    """What the person types for this option (the line in its own grammar)."""
    if meant is None: return line
    k, n = meant
    m = re.match(r"(\w+ [\d.]+ \w+) ", line)
    head = m.group(1) if m else line
    if option == "C2": return f"{head} {KIND_NOUN[k]} {n}"
    if option == "C3": return f"{head} {MARK[k]}{n}"
    return f"{head} {n}"

def run(option):
    wrong = asks = effort = 0
    for line, meant, part in CASES:
        items = UNIQUE if option == "C4" else ITEMS
        if option == "C4" and meant == ("cat", "Holidays"): meant = ("cat", "Entertainment")   # it had to be named otherwise
        typed = line if line.endswith((" fun", " car")) else written(option, line, meant)
        if option == "C5" and meant is not None:
            m = re.match(r"(\w+ [\d.]+ \w+) ", line)
            head = m.group(1) if m else line
            typed, chip = f"{head} {part}", 1        # type the part word, tap its chip: the tap is the choice, not a guess
            effort += len(typed) + chip + 1           # + Confirm
            continue
        verdict, got = {"C1": bare, "C4": lambda s: bare(s, items)}.get(option, {"C2": nouns, "C3": marks}.get(option, bare))(typed)
        if option == "C3": effort += 2 * typed.count("^") + 2 * typed.count("#") + 2 * typed.count("@")   # the symbol layer, there and back
        effort += len(typed) + 1
        if verdict == "ask": asks += 1; effort += 2              # the question's chip, then Confirm again
        elif got != meant: wrong += 1
    return wrong, asks, effort

def w_control():
    """W: the + of the target's own row (or the floating + and the picker): no reading, so no wrong and no ask."""
    effort = 0
    for line, meant, _ in CASES:
        digits = len(re.search(r"[\d.]+", line).group(0))
        effort += 1 + digits + 1 + (0 if meant is None else 0)   # the row's +, the amount, Confirm
    return 0, 0, effort

RULES = {"C1": 8, "C2": 11, "C3": 11, "C4": 8, "C5": 8, "W": 0}
# C1: verbs (spent, paid, add, received, charge), prepositions (on, to), the direction word; C2/C3 add three nouns or marks.
# Nothing is typed in W: every word is a control showing its own options.

# ---- part 2: arrangements -----------------------------------------------------------------------------------------
K, P, B, H, M = 0.28, 1.1, 0.2, 0.4, 1.35
TASKS = [  # task, line typed, amount keys, kind, target
    ("Spent 30 on Entertainment", "spent 30 on ent", 2, "cat", "Entertainment"),
    ("Add 50 to Holidays", "add 50 to hol", 2, "goal", "Holidays"),
    ("Coffee 3.50 on Dining out", "3.50 coffee", 4, "cat", "Dining out"),   # was "out of Savings" until 2026-10-09
    ("Charge 40 on Credit Card", "charge 40 on cre", 2, "acct", "Credit Card"),
    ("Received 1450 as Income", "received 1450", 4, "savings", "Savings"),
]

def arrangement(last=("cat", "Entertainment")):
    rows = []
    for task, typed, digits, kind, target in TASKS:
        # W alone, target's screen: the row's + (Savings: the floating +, which opens it when last used); amount; Confirm
        dirtap = 1 if task.startswith("Received") else 0      # W: the direction word, Money in -> Income
        w_here_phone = 1 + dirtap + digits + 1
        w_here_desk = M + P + B + dirtap * (P + B) + H + digits * K + K   # point at +, click, hand to keys, amount, Enter
        # W alone, another screen: the floating + opens the last kind and target. Another target in the same group:
        # the target word, then its chip; another group: the selector (it opens the group's chips), then the chip;
        # Savings: the selector alone (one item)
        repick = 0 if (kind, target) == last else 1 if kind == "savings" else 2
        w_away_phone = 1 + repick + dirtap + digits + 1
        w_away_desk = M + P + B + (repick + dirtap) * (P + B) + H + digits * K + K
        # line (C5) on every screen: tap the line, type, one chip, Confirm/Enter (same here or away)
        chip = 0 if kind == "savings" else 1
        l_phone = 1 + len(typed) + chip + 1
        l_desk = M + P + B + H + len(typed) * K + chip * (H + P + B) + K
        rows.append((task, w_here_phone, w_away_phone, l_phone, w_here_desk, w_away_desk, l_desk))
    return rows

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    print("PART 1: how the line finds its target (14 cases, seed names + Holidays)\n")
    print(f"{'option':34} {'wrong':>5} {'asks':>5} {'phone effort':>12} {'rules to know':>13}  front")
    res = {o: run(o) for o in ("C1", "C2", "C3", "C4", "C5")}
    res["W"] = w_control()
    names = {"C1": "C1 bare names", "C2": "C2 kind nouns", "C3": "C3 marks (^ # @, legend)", "C4": "C4 bare + unique names",
             "C5": "C5 bare + name chips", "W": "W  sentence, picker (control)"}
    for o, (wr, a, e) in res.items():
        dom = [p for p, (w2, a2, e2) in res.items() if p != o and w2 <= wr and a2 <= a and e2 <= e and RULES[p] <= RULES[o]
               and (w2, a2, e2, RULES[p]) != (wr, a, e, RULES[o])]
        print(f"{names[o]:34} {wr:5} {a:5} {e:12} {RULES[o]:13}  {'yes' if not dom else 'dominated by ' + ', '.join(dom)}")
    # control: a matcher taking the first name word found, with no tie and no preposition rule, must write wrongly
    first = lambda s: next((("write", i) for i in ITEMS if norm(i[1].split()[0]) in norm(s)), ("write", None))
    bad = sum(1 for line, meant, _ in CASES if first(line)[1] != meant)
    print(f"{'control: first name word found':34} {bad:5}   (must be > 0, or the cases prove nothing)")
    print("\nWrong writes must be 0 (4.3: never a guess). Effort = keys + taps for all 14 lines, asks included.")
    print("\nPART 2: the line on every screen + W on each +, against W alone\n")
    for last in (("cat", "Entertainment"), ("goal", "Holidays")):
        print(f"floating + last used for: {last[1]}")
        print(f"{'task':30} {'phone: W here':>13} {'W away':>7} {'line':>5}   {'desktop s: W here':>17} {'W away':>7} {'line':>6}")
        rows = arrangement(last); tot = [0] * 6
        for r in rows:
            print(f"{r[0]:30} {r[1]:13} {r[2]:7} {r[3]:5}   {r[4]:17.2f} {r[5]:7.2f} {r[6]:6.2f}")
            tot = [a + b for a, b in zip(tot, r[1:])]
        print(f"{'total':30} {tot[0]:13} {tot[1]:7} {tot[2]:5}   {tot[3]:17.2f} {tot[4]:7.2f} {tot[5]:6.2f}\n")
    print("The line's fixed cost: 56 dp on every screen (Learn, Reports and Settings included), and the rules above.")

if __name__ == "__main__":
    main()
