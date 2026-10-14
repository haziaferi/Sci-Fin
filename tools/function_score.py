"""The owner's two function axes (2026-10-08), over the from-scratch candidates.

  visual       does prominence follow importance? Kendall's tau-b between each element's purpose
               rank (design/scratch-2026-10-08/inventory.md) and four prominence measures, read
               from the render's pixels:
                 size    the element's tallest glyph or control, in dp
                 weight  stroke width / glyph height (bold strokes are thicker for their size)
                 colour  CIEDE2000 between the element's ink and its own background
                 squint  the render blurred (Gaussian, SQUINT_DP), the largest lightness
                         difference inside the element from the page ground
               An element below the first screen, or with no visible affordance, measures 0.
  interactive  is each control where a person expects it? Three sub-scores:
                 refs      share of the reference apps' observed placements that match the
                           candidate's, per kind of control (REFS; kinds not seen in the captures
                           are not scored)
                 platform  Material 3 rules that tell the candidates apart (PLATFORM)
                 today     share of control kinds left where today's Sci-Fin has them

Elements are found by OCR (Tesseract word boxes, refs_score.ocr_words) and assigned by the
section probes ScratchRenderTest writes; controls come from the probes themselves. Light and dark
renders are both measured and averaged.

    python tools/function_score.py            # the table, the Pareto fronts, build/scratch/function.json
    python tools/function_score.py --explain  # every element's boxes and measures, per screen
"""
import json, math, os, sys
from PIL import Image, ImageCms, ImageFilter
sys.path.insert(0, os.path.dirname(__file__))
import refs_score, scratch_score

D = scratch_score.D
DIR = scratch_score.DIR
NAV_TOP_DP = scratch_score.SCREEN_DP - scratch_score.NAV_DP
TITLE_DP = 55                    # the page title ("Budget", "Activity") sits above this
SQUINT_DP = float(os.environ.get("SCIFIN_SQUINT_DP", 4))   # blur radius standing in for a squint
INK_DE = 15                      # a pixel is ink when it differs from its background by more than this (CIE76)
QUANT = {"size": 2.0, "weight": 0.02, "colour": 5.0, "squint": 5.0}   # differences below these are ties

ELEMENTS = {
    "budget": {"savings": 1, "expense": 2, "goals": 3, "goal_add": 4, "income": 5, "recurring": 6,
               "log_income": 7, "new_goal": 8, "edit_savings": 9, "learn": 10, "switch": 11},
    "activity": {"savings": 1, "expense": 2, "accounts": 3, "log": 4, "import": 5, "log_income": 6,
                 "new_account": 7, "edit_savings": 8, "note": 9},
}

# ---- where each candidate puts each control (read from ScratchScreens.kt, BudgetScreen.kt, ActivityScreens.kt, SavingsRow.kt)
PLACEMENT = {
    #               expense           goal_add          log_income            edit_savings         open_item  new_item    row_chevron  account_charge
    "today":       ("savings-icon",   "row-icon",       "savings-icon",       "savings-icon",      "row-tap", "list-end", "none",      "row-icon"),
    "ledger-hero": ("savings-icon",   "row-icon",       "savings-under",      "savings-under",     "row-tap", "list-end", "none",      "row-icon"),
    "M1":          ("fab-bottom-end", "fab-then-pick",  "savings-tap-sheet",  "savings-row-tap",   "row-tap", "list-end", "chevron",   "row-tap-detail"),
    "M2":          ("savings-icon",   "row-icon",       "savings-tap-sheet",  "savings-row-tap",   "row-tap", "list-end", "none",      "row-icon"),
    "M3":          ("fab-bottom-end", "row-icon",       "savings-tap-sheet",  "savings-row-tap",   "row-tap", "list-end", "chevron",   "row-tap-detail"),
}
KINDS = ["expense", "goal_add", "log_income", "edit_savings", "open_item", "new_item", "row_chevron", "account_charge"]

# ---- what the reference captures show (design/refs-apps/*.png and controls-dump.txt); kinds not seen are absent
REFS = {
    "expense": {"fab-bottom-end": ["Bluecoins", "Cashew", "Goodbudget", "MMEX", "MoneyManager", "My Expenses", "Wallet"],
                "nav-centre": ["Ivy"], "bottom-split-buttons": ["Monefy"], "keypad-on-screen": ["Buckwheat"]},
    "goal_add": {"row-icon": ["YNAB (plan: + per category)"], "fab-then-pick": ["Goodbudget (Fill Envelopes)", "YNAB (Assign)"]},
    "new_item": {"list-end": ["Cashew (budgets)", "Wallet (goals)", "Goodbudget (accounts)"], "fab": ["My Expenses (accounts)"]},
    "open_item": {"row-tap": ["Bluecoins", "Cashew", "MoneyManager", "My Expenses", "Buckwheat", "Ivy"]},
    "row_chevron": {"none": ["Bluecoins", "Cashew", "MoneyManager", "Buckwheat", "Ivy"], "chevron": ["My Expenses"]},
}

# ---- Material 3 rules that tell the candidates apart; rules every candidate meets are listed for the record
PLATFORM = [
    ("The screen's most important action is a FAB, bottom end", lambda pl: pl[0] == "fab-bottom-end"),
    ("An action on one item sits on that item (a list item's trailing control)", lambda pl: pl[1] == "row-icon"),
]
PLATFORM_ALL_PASS = ["Tapping a list item opens it", "One FAB at most per screen", "Touch targets of 48dp or more",
                     "Navigation bar holds destinations, not actions"]

# ---------------------------------------------------------------- pixels
_LAB = ImageCms.buildTransform(ImageCms.createProfile("sRGB"), ImageCms.createProfile("LAB"), "RGB", "LAB")

def lab_image(png):
    im = Image.open(png).convert("RGB")
    return ImageCms.applyTransform(im, _LAB)

def to_lab(p):
    return (p[0] / 2.55, p[1] - 128, p[2] - 128)

def de76(a, b):
    return math.dist(a, b)

def de2000(l1, l2):
    L1, a1, b1 = l1; L2, a2, b2 = l2
    C1, C2 = math.hypot(a1, b1), math.hypot(a2, b2); Cb = (C1 + C2) / 2
    G = 0.5 * (1 - math.sqrt(Cb ** 7 / (Cb ** 7 + 25 ** 7)))
    a1p, a2p = (1 + G) * a1, (1 + G) * a2
    C1p, C2p = math.hypot(a1p, b1), math.hypot(a2p, b2)
    h1p = math.degrees(math.atan2(b1, a1p)) % 360; h2p = math.degrees(math.atan2(b2, a2p)) % 360
    dLp, dCp = L2 - L1, C2p - C1p
    dh = 0 if C1p * C2p == 0 else (h2p - h1p if abs(h2p - h1p) <= 180 else h2p - h1p - 360 if h2p > h1p else h2p - h1p + 360)
    dHp = 2 * math.sqrt(C1p * C2p) * math.sin(math.radians(dh / 2))
    Lbp, Cbp = (L1 + L2) / 2, (C1p + C2p) / 2
    hbp = h1p + h2p if C1p * C2p == 0 else ((h1p + h2p) / 2 if abs(h1p - h2p) <= 180 else (h1p + h2p + 360) / 2 if h1p + h2p < 360 else (h1p + h2p - 360) / 2)
    T = 1 - 0.17 * math.cos(math.radians(hbp - 30)) + 0.24 * math.cos(math.radians(2 * hbp)) + 0.32 * math.cos(math.radians(3 * hbp + 6)) - 0.20 * math.cos(math.radians(4 * hbp - 63))
    SL = 1 + 0.015 * (Lbp - 50) ** 2 / math.sqrt(20 + (Lbp - 50) ** 2); SC = 1 + 0.045 * Cbp; SH = 1 + 0.015 * Cbp * T
    RT = -2 * math.sqrt(Cbp ** 7 / (Cbp ** 7 + 25 ** 7)) * math.sin(math.radians(60 * math.exp(-((hbp - 275) / 25) ** 2)))
    return math.sqrt((dLp / SL) ** 2 + (dCp / SC) ** 2 + (dHp / SH) ** 2 + RT * (dCp / SC) * (dHp / SH))

def median_lab(px):
    if not px: return None
    return tuple(sorted(c[i] for c in px)[len(px) // 2] for i in range(3))

class Screen:
    def __init__(self, png):
        self.lab = lab_image(png); self.W, self.H = self.lab.size
        self.px = self.lab.load()
        L = self.lab.getchannel(0)
        self.blur = L.filter(ImageFilter.GaussianBlur(SQUINT_DP * D)).load()
        hist = L.histogram(); self.ground = hist.index(max(hist)) / 2.55        # the page ground is the commonest lightness

    def box_mode(self, x, y, w, h):
        """A word's background: its box's commonest colour (glyphs cover well under half a tight word box)."""
        counts = {}; sums = {}
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                c = to_lab(self.px[xx, yy]); k = (round(c[0] / 3), round(c[1] / 3), round(c[2] / 3))
                counts[k] = counts.get(k, 0) + 1; s = sums.setdefault(k, [0, 0, 0])
                for i in range(3): s[i] += c[i]
        k = max(counts, key=counts.get)
        return tuple(v / counts[k] for v in sums[k])

    def ring(self, x, y, w, h, sides_only=False):
        o1, o2 = int(2 * D), int(5 * D); out = []
        for yy in list(range(y - o2, y - o1)) + list(range(y + h + o1, y + h + o2)):
            if 0 <= yy < self.H:
                out += [to_lab(self.px[xx, yy]) for xx in range(max(0, x), min(self.W, x + w), 2)]
        if not sides_only:
            for xx in list(range(x - o2, x - o1)) + list(range(x + w + o1, x + w + o2)):
                if 0 <= xx < self.W:
                    out += [to_lab(self.px[xx, yy]) for yy in range(max(0, y), min(self.H, y + h), 2)]
        return median_lab(out)

    def measure(self, box, text):
        x, y, w, h = (int(v) for v in box)
        x, y = max(0, x), max(0, y); w, h = min(w, self.W - x), min(h, self.H - y)
        if w <= 2 or h <= 2: return None
        bg = self.box_mode(x, y, w, h) if text else self.ring(x, y, w, h)
        ink = set(); cols = []
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                c = to_lab(self.px[xx, yy])
                if de76(c, bg) > INK_DE: ink.add((xx, yy)); cols.append(c)
        if len(ink) < 4: return dict(size=0, weight=0, colour=0, squint=0)
        ys = sorted(p[1] for p in ink)
        height = h if text else (ys[-1] - ys[0] + 1)              # a word's OCR box is its glyph box; a control's ink is measured
        perim = sum(1 for (a, b) in ink if (a + 1, b) not in ink or (a - 1, b) not in ink or (a, b + 1) not in ink or (a, b - 1) not in ink)
        stroke = 2 * len(ink) / max(1, perim)
        sq = max(abs(self.blur[xx, yy] / 2.55 - self.ground) for yy in range(y, y + h, 2) for xx in range(x, x + w, 2))
        return dict(size=height / D, weight=stroke / height, colour=de2000(median_lab(cols), bg), squint=sq)

# ---------------------------------------------------------------- elements
def lines(words):
    by = {}
    for w in words: by.setdefault(w["line"], []).append(w)
    return [sorted(ws, key=lambda w: w["x"]) for ws in by.values()]

def assign(png, probes, tab, model):
    """element -> [(box px, is_text)], for the first screen."""
    words, _ = refs_score.ocr_words(png)
    nav_top = NAV_TOP_DP * D
    secs = sorted(((p["y"], p["name"][8:]) for p in probes if p["name"].startswith("section:")))
    first_sec = secs[0][0] if secs else 1e9
    def region(y):
        r = "top"
        for sy, n in secs:
            if y >= sy - 4 * D: r = n
        return r
    out = {e: [] for e in ELEMENTS[tab]}
    log_rows = []                                             # Activity's two nav rows: (y, element)
    for ln in lines(words):
        y = min(w["y"] for w in ln); yb = max(w["y"] + w["h"] for w in ln)
        if yb > nav_top or y < TITLE_DP * D: continue
        text = " ".join(w["t"] for w in ln).lower()
        letters = [c for c in " ".join(w["t"] for w in ln) if c.isalpha()]
        if letters and all(c.isupper() for c in letters) and any(abs(y - sy) < 12 * D for sy, _ in secs): continue   # a section title
        r = region(y + (yb - y) / 2)
        e = None
        if tab == "budget":
            if text.startswith("why") or " why " in f" {text} ": e = "learn"
            elif "new goal" in text: e = "new_goal"
            elif r == "top" and ("envelopes" in text or "categories" in text): e = "switch"
            elif r == "top": e = "savings"
            else: e = {"Goals": "goals", "Income": "income", "Recurring expenses": "recurring"}.get(r)
        else:
            if "new account" in text: e = "new_account"
            elif "informational" in text: e = "note"
            elif "activity log" in text or "log" == text.strip(): e = "log"; log_rows.append((y, e))
            elif "import" in text: e = "import"; log_rows.append((y, e))
            elif r == "top": e = "savings"
            elif r == "Accounts": e = "accounts"
            elif r == "Log":
                above = [ro for ro in log_rows if ro[0] <= y]
                e = (max(above)[1] if above and y - max(above)[0] < 30 * D else "note")
        # icon glyphs OCR misreads as "[3", "©" or "yay)" are not text: keep words with two or more letters or digits
        if e: out[e] += [((w["x"], w["y"], w["w"], w["h"]), True) for w in ln if sum(c.isalnum() for c in w["t"]) >= 2]
    def ctl(p): return ((p["x"], p["y"], p["w"], p["h"]), False)
    first = [p for p in probes if p["y"] + p.get("h", 0) <= nav_top]
    fab = [p for p in first if p["name"] == "el:fab"]
    if model in ("M1", "M3"): out["expense"] += [ctl(p) for p in fab]
    else: out["expense"] += [ctl(p) for p in first if p["name"] == "round:Quick-add" and p["y"] < first_sec][:1]
    if tab == "budget":
        gy = next((sy for sy, n in secs if n == "Goals"), None)
        if model == "M1": out["goal_add"] += [ctl(p) for p in fab]
        elif gy is not None:
            nxt = min([sy for sy, _ in secs if sy > gy] + [1e9])
            out["goal_add"] += [ctl(p) for p in first if p["name"].startswith("round:") and gy < p["y"] < nxt]
    # the outlined "+ New ..." rows: OCR cannot read them, so their probe; inset past the outline to measure the label
    ix, iy = 16 * D, 4 * D                 # past the outline and its rounded corners
    for p in first:
        if p["name"] in ("el:add:New goal", "el:add:New account"):
            out["new_goal" if tab == "budget" else "new_account"].append(((p["x"] + ix, p["y"] + iy, p["w"] - 2 * ix, p["h"] - 2 * iy), False))
    out["log_income"] += [ctl(p) for p in first if p["name"] == "round:Log income"]
    out["edit_savings"] += [ctl(p) for p in first if p["name"] == "round:Edit Savings directly"]
    return out

# ---------------------------------------------------------------- scores
def tau_b(x, y):
    n = len(x); c = d = tx = ty = 0
    for i in range(n):
        for j in range(i + 1, n):
            dx, dy = x[i] - x[j], y[i] - y[j]
            if dx == 0 and dy == 0: continue
            if dx == 0: tx += 1
            elif dy == 0: ty += 1
            elif dx * dy > 0: c += 1
            else: d += 1
    den = math.sqrt((c + d + tx) * (c + d + ty))
    return (c - d) / den if den else 0.0

def visual(png, probes, tab, model, explain=False):
    s = Screen(png); els = assign(png, probes, tab, model)
    meas = {}
    for e, boxes in els.items():
        ms = [m for m in (s.measure(b, t) for b, t in boxes) if m]
        # size, colour and squint: the element at its most prominent; weight: the stroke of its tallest box
        meas[e] = {k: max([m[k] for m in ms], default=0) for k in QUANT}
        if ms: meas[e]["weight"] = max(ms, key=lambda m: (m["size"], m["weight"]))["weight"]
    names = list(ELEMENTS[tab]); imp = [-ELEMENTS[tab][e] for e in names]
    taus = {k: tau_b(imp, [round(meas[e][k] / QUANT[k]) for e in names]) for k in QUANT}
    if explain:
        print(f"  {os.path.basename(png)}")
        for e in names:
            m = meas[e]; print(f"    {ELEMENTS[tab][e]:2} {e:13} boxes {len(els[e]):2}  size {m['size']:5.1f}  weight {m['weight']:.3f}  colour {m['colour']:5.1f}  squint {m['squint']:5.1f}")
    return taus, meas

def interactive(model):
    pl = dict(zip(KINDS, PLACEMENT[model]))
    shares = {}
    for kind, obs in REFS.items():
        total = sum(len(v) for v in obs.values())
        shares[kind] = len(obs.get(pl[kind], [])) / total
    refs = sum(shares.values()) / len(shares)
    platform = sum(rule(PLACEMENT[model]) for _, rule in PLATFORM) / len(PLATFORM)
    today = sum(a == b for a, b in zip(PLACEMENT[model], PLACEMENT["today"])) / len(KINDS)
    return dict(refs=refs, refs_by_kind=shares, platform=platform, today=today)

def pareto(rows, keys):
    """keys: [(key, +1 higher is better | -1 lower is better)]; returns {name: [names that dominate it]}."""
    dom = {}
    for a in rows:
        dom[a["name"]] = [b["name"] for b in rows if b is not a
                          and all(s * b[k] >= s * a[k] - 1e-9 for k, s in keys) and any(s * b[k] > s * a[k] + 1e-9 for k, s in keys)]
    return dom

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    explain = "--explain" in sys.argv
    old = {r["name"]: r for r in json.load(open(os.path.join(DIR, "scores.json")))}
    rows = []
    for v in old:
        model = v.split("-")[0] if v.startswith("M") else v
        vis = {k: [] for k in QUANT}; per_screen = {}
        for tab in ("budget", "activity"):
            for mode in ("light", "dark"):
                stem = f"{v}-{mode}-{tab}"
                taus, meas = visual(os.path.join(DIR, stem + ".png"), scratch_score.load(stem), tab, model, explain)
                per_screen[stem] = dict(taus=taus, measures=meas)
                for k in QUANT: vis[k].append(taus[k])
        r = dict(name=v, **{f"vis_{k}": sum(x) / len(x) for k, x in vis.items()})
        r["visual"] = sum(r[f"vis_{k}"] for k in QUANT) / len(QUANT)
        it = interactive(model); r.update(int_refs=it["refs"], int_platform=it["platform"], int_today=it["today"], refs_by_kind=it["refs_by_kind"])
        r["interactive"] = (it["refs"] + it["platform"] + it["today"]) / 3
        o = old[v]
        al = [a for a in o["aligned"] if a is not None]
        r.update(order=sum(o["order"]) / 2, taps=sum(o["taps"]), reach=o["reach"], covered=o["covered"], aligned=sum(al) / len(al) if al else 0)
        r["screens"] = per_screen
        rows.append(r)
    print(f"{'candidate':20} {'size':>5} {'weight':>6} {'colour':>6} {'squint':>6} {'VISUAL':>7} | {'refs':>5} {'platf':>5} {'today':>5} {'INTER':>6} | order taps reach cov align")
    for r in rows:
        print(f"{r['name']:20} {r['vis_size']:5.2f} {r['vis_weight']:6.2f} {r['vis_colour']:6.2f} {r['vis_squint']:6.2f} {r['visual']:7.2f} | "
              f"{r['int_refs']:5.2f} {r['int_platform']:5.2f} {r['int_today']:5.2f} {r['interactive']:6.2f} | {r['order']:5.2f} {r['taps']:4} {r['reach']*100:4.0f}% {r['covered']:3} {r['aligned']*100:4.0f}%")
    two = pareto(rows, [("visual", 1), ("interactive", 1)])
    allk = [("vis_size", 1), ("vis_weight", 1), ("vis_colour", 1), ("vis_squint", 1), ("int_refs", 1), ("int_platform", 1), ("int_today", 1),
            ("order", 1), ("taps", -1), ("reach", 1), ("covered", -1), ("aligned", 1)]
    every = pareto(rows, allk)
    print("\nPareto front, the two function axes:", ", ".join(n for n, d in two.items() if not d))
    for n, d in two.items():
        if d: print(f"  {n:20} dominated by {', '.join(d)}")
    print("Pareto front, every measure:", ", ".join(n for n, d in every.items() if not d))
    for n, d in every.items():
        if d: print(f"  {n:20} dominated by {', '.join(d)}")
    json.dump(dict(rows=rows, pareto_two=two, pareto_all=every, placement=PLACEMENT, refs=REFS,
                   platform=[p for p, _ in PLATFORM], platform_all_pass=PLATFORM_ALL_PASS),
              open(os.path.join(DIR, "function.json"), "w", encoding="utf-8"), indent=1, ensure_ascii=False)

if __name__ == "__main__":
    main()
