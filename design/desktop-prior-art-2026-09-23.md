# Desktop prior art for §4.8 — open-source personal-finance apps

**Date:** 2026-09-23
**Status:** working evidence, outside the spec. Nothing here has been written into `scifinspec.md`,
by decision — this file is the record, §4.8 will cite only its own conclusion.
**Question it was built to answer:** §4.8 was reopened on 2026-09-23 ("the desktop app will not be a
copy of the phone app"). Five candidates A–E were drawn in `design/desktop-options.html` and a
synthesis, **E on Budget + C's reservation rule elsewhere**, was recommended. This survey tests that
recommendation against what comparable desktop apps actually ship.

**Headline result: the recommendation does not survive. E+C is retracted.** It ranks 56th of 180
feasible structures and beats plain C under 0% of 4,000 random reweightings. Details in §5–6.

---

## 1. Method, and what it is worth

Open-source desktop-first apps were preferred over commercial ones for a specific reason: they ship
their *layout source and running instances*, so pane geometry is a number that can be read rather
than a pixel estimated off a posed marketing render at a width nobody uses.

Evidence strength is recorded per row and carried into the scoring as a weight:

| strength | meaning |
|---|---|
| 1.0 | measured in a running instance at Sci-Fin's own window size |
| 0.8 | official documentation describing the layout in structural terms |
| 0.5–0.7 | secondary sources, issue trackers, or docs that describe function but not structure |

**One candidate could not be verified and is excluded rather than guessed at.** HomeBank's
documentation host `homebank.free.fr` is HTTP-only and refuses TLS (`ECONNREFUSED …:443`), the
project has moved to `gethomebank.org` where the help path 404s, and the site's screenshot page is
behind a 210-partner tracking-consent wall that was not accepted. HomeBank therefore appears in no
count in this document. Six apps carry the structural findings; a seventh, Maybe, is counted at
reduced weight because the project is no longer maintained.

---

## 2. The survey

| App | Shell (left region) | Budget screen | Two co-equal views side by side? | Verdict colour | Ev. |
|---|---|---|---|---|---|
| **Actual Budget** | data sidebar, **240 px measured**, carries nav *and* every account with its balance | sidebar + 860 px main, table of `Categoria / Preventivato / Speso / Saldo` | **Yes** — accounts ‖ categories, permanently | **Yes**, and deliberate (§4) | 1.0 |
| **GnuCash** | none — tabbed notebook; named areas are titlebar, menubar, toolbar, tab bar, main pane, summarybar, statusbar | one main pane, one tab at a time | No | not established | 0.8 |
| **KMyMoney** | budget list on the left | right side splits into three: categories tree (top), controls (middle), assignments for the selected category (bottom) | No — list → tree → detail, a chain not a pair | **None described** in the Budgets view | 0.8 |
| **Money Manager Ex** | navigation panel, hierarchical tree | "Workspace area" shows the selected section | No — budget is a destination in the tree | not established | 0.7 |
| **Skrooge** | dockable panels, tabs, bookmark hierarchy — user-arranged | whatever the user docks | User's choice | not established | 0.6 |
| **Firefly III** | web sidebar | one main pane; budget bars | No | **Yes** — red→orange overspend bars, green "left to spend" box | 0.7 |
| **Maybe** | sidebar with account trends | sidebar + main | No | not established | 0.5, unmaintained |

### Two findings the table makes unanimous

**a. Nobody uses an icon rail.** Five of seven use a data sidebar, one uses tabs, one uses docks.
**Zero of seven** put a narrow icon-only rail in the left region — which is exactly what Sci-Fin's
§4.8 geometry specifies at `u*11` = 88 dp. Every surveyed app spends that region on either data
(account names and balances) or navigation history (tabs), not on five icons.

**b. Side-by-side exists, but only once, and not in E's form.** Actual is the only app that puts two
co-equal sets of figures on the budgeting screen at the same time. It is also the most successful
and most actively developed of the seven, and it is the closest functional match to Sci-Fin's Budget
tab (envelope / zero-sum). So E's structure is not exotic. But Actual's version of it pairs the
side-by-side with a **data sidebar and inline editors**, where E pairs it with a **rail and dialogs**
— a different combination, and a worse-scoring one (§5).

---

## 3. Measured: Actual Budget at 1100 × 760

Loaded in the public demo at Sci-Fin's own desktop window size and measured from the DOM, not
estimated from a screenshot.

```
viewport            1100 × 760
sidebar               240 px at x=0     (nav + all accounts + balances, full height)
main pane             860 px at x=240
budget table          636 px wide, x=358 … 994
  Categoria           141 px at x=358
  Preventivato        148 px at x=531
  Speso               148 px at x=689
  Saldo               148 px at x=846
adjacent months rendered off-screen at x=728 and x=1213 for horizontal scroll
```

For comparison, Sci-Fin's own measured desktop geometry: rail **88**, list pane **368**, detail pane
**644**, window 1100 × 760. Candidate E's columns were **89 / 506 / 505**.

Actual's sidebar is **2.7× Sci-Fin's rail**, and the difference is entirely content: it holds
"Tutti i conti", "In budget" and "Fuori budget" groups with a running balance against each account.

---

## 4. The register finding, and a correction to a first reading

A screenshot suggested Actual renders negative numbers in red. **Measuring the computed colours
shows that is wrong, and the truth is more interesting.**

| value | computed colour | reading |
|---|---|---|
| `-770.24`, `-3,231.47`, `-1,960.91`, `-170.12` | `rgb(217,226,236)` | ordinary text — a minus sign alone gets no colour |
| overspent category balances (`-271.20`, `-153.46`, `-11.04`) | `rgb(255,155,155)` | **red — a verdict** |
| `To Budget: 0.00` | `rgb(101,214,173)` | **green — a verdict** |

So Actual applies valence colour to exactly two *states*, not to sign: **"this envelope is
overspent"** and **"every euro is assigned"**. Firefly III does the same thing more loudly — red
shading to orange by overspend magnitude, plus a green "left to spend" box, with an open issue
devoted to tuning which colour means what.

This is precisely what Sci-Fin's §2.1 prohibits (no valence colour anywhere; one accent per theme)
and what §2.2/§2.4 call the scoreboard.

**What it does and does not prove.** §4.8's original argument was that a desktop IA showing every
figure at once *becomes* the scoreboard §2.2/§2.4 exist to prevent. Actual is consistent with that
worry — wide layout and traffic lights together — but it does not establish the causal link, because
its red/green is a method choice inherited from envelope budgeting's YNAB lineage and is present in
its narrow layouts too.

**KMyMoney is the counterexample that settles it.** It has the widest, most figure-dense budget
layout in the survey — a list, a full category tree, and an assignments panel all at once — and its
documentation describes **no over/under colour coding at all**. Width and verdict are therefore
**independent choices**. §4.8's sentence conflated them. Sci-Fin can take the width without taking
the scoreboard, provided it keeps §2.1, which it has an explicit prohibition to do.

That is the strongest single result in this survey, and it is good news for the register argument:
the thing §4.8 feared is avoidable by a rule Sci-Fin already has.

> **Weakened by §10.2 (same day).** A second app, Fava, was briefly cited here as confirming
> this. It does not: its verdict is carried in its *language* ("Income Statement", "Net Profit"),
> not its colour, so it is closer to an example of §4.8's fear than a rebuttal. The claim above
> now rests on KMyMoney alone, which is documentation evidence — a handbook that does not
> describe colour coding is not an observation that there is none. **Read this section's
> conclusion as an open question, not a finding.**

---

## 5. Scoring the candidates

The five drawn candidates, the E+C synthesis and Actual's shape were placed as points in a
six-dimension structural space and scored on one fitness function. All six components are
arithmetic — the run needed **zero model turns**.

Space: `shell` × `budget_panes` × `coequal_budget` × `other_tabs` × `editor` × `verdict_colour`
= 432 points, **180 feasible** after §2.1's prohibition on verdict colour and a coherence
constraint.

| component | weight | what it measures |
|---|---|---|
| `spec_conformance` | 0.30 | §2.1's prohibition (hard zero), §4.2's five fixed destinations |
| `figures_density` | 0.20 | §2.2/§2.4 calm register: how many distinct figure-sets are co-visible |
| `prior_art` | 0.20 | how much of the surveyed field ships this combination, evidence-weighted |
| `width_use` | 0.15 | 1100 px left empty is as much a defect as 1100 px overfilled |
| `phone_fork_cost` | 0.10 | every divergence from the phone build is code that exists twice |
| `unwritten_deps` | 0.05 | a reserved right pane needs §4.5's four unwritten Learn bodies |

The components were written from the spec's own stated constraints before the candidates were
scored, and the scorer source is in the appendix so the numbers can be re-derived or disagreed with.

### Results — exhaustive, not sampled

The space is small enough to enumerate, so the optimum below is **proven, not searched**. The
optimizer's own run (HS+DE, converged at generation 3, then stalled twelve generations at 0.818)
found the same point.

```
exact optimum   0.8182   icon_rail · coequal=no · reserved_right_pane · inline editors

the named candidates
  0.8102  rank   4/180   C Workbench
  0.7872  rank  20/180   A Reflow
  0.7872  rank  20/180   B Centred
  0.7872  rank  20/180   D At scale
  0.7189  rank  44/180   Actual's shape
  0.7075  rank  52/180   E Side-by-side
  0.7034  rank  56/180   E+C synthesis      <-- the retracted recommendation
```

### Why E+C loses, component by component

```
                      C       E+C     delta    weighted
spec_conformance    1.000    1.000   +0.000    +0.0000
figures_density     1.000    0.720   -0.280    -0.0560
prior_art           0.273    0.240   -0.034    -0.0067
width_use           1.000    1.000   +0.000    +0.0000
phone_fork_cost     0.780    0.340   -0.440    -0.0440
unwritten_deps      0.550    0.550   +0.000    +0.0000
```

Two things sink it, and the larger one was not part of the original argument at all:

1. **`phone_fork_cost` 0.34 vs 0.78.** E+C forks *three* things from the phone build simultaneously
   — a side-by-side Budget, a reserved right pane, and dialog editors. C forks one.
2. **`figures_density` 0.72 vs 1.00.** Two figure-sets on one screen, which is the original §4.8
   objection, correctly priced but *smaller* than the fork cost.

**The prior-art term did not rescue E+C — it scored it lower than C** (0.240 vs 0.273). The field
does not support E+C's combination, because the one app that does side-by-side does it with a data
sidebar and inline editors. "Actual's shape" scores 0.7189, comfortably above both E (0.7075) and
E+C (0.7034): **if side-by-side is wanted, Actual's version of it is the better one.**

### Sensitivity — is this an artefact of my weights?

4,000 random reweightings of the six components:

```
  58.3%   A Reflow
  33.8%   C Workbench
   7.8%   Actual's shape
   0.1%   E Side-by-side
   0.0%   E+C synthesis

  E+C beats C under 0.0% of random weightings
```

E+C is not a close call under a particular weighting. It loses under essentially all of them.

The genuinely weight-dependent question is **A vs C** — whether to reserve a right pane at all. A
wins on most weightings because it forks less; C wins when `width_use` is weighted up. That is a
real choice, not a computed one.

---

## 6. What this changes

**Retracted:** the recommendation of "E on Budget, C's rule elsewhere". It was argued from the fact
that Budget is the only tab in §4.2's table with two co-equal views of one thing. That observation
is still true — it just turns out not to be worth three simultaneous forks of the phone build.

**Standing:** the register argument in §4.8 is *weakened* by the survey, not strengthened. KMyMoney
shows width and verdict-colour are independent, so the reopened section does not need to choose a
narrow layout to stay calm; it needs to keep §2.1, which it already mandates.

**Live options, in scored order:** C Workbench (0.8102, rank 4) or A Reflow (0.7872, rank 20), with
the exact optimum being C with **inline** editors rather than push (0.8182, +0.008).

---

## 7. The three open sub-decisions, against prior art

| Open question | What the survey says |
|---|---|
| **Does the icon rail survive?** | **0 of 7** surveyed apps use one. The rail still wins the fitness (best rail point 0.8182 vs best data-sidebar point 0.7977) but only by 0.0205, and only because `figures_density` penalises the always-visible balances a data sidebar implies. The field is unanimous the other way. This is the survey's sharpest challenge to the current geometry and deserves its own decision. |
| **Do §4.8 rule 3's dialogs survive?** | Weakly supported. Two of the evidenced apps use dialogs, two use inline editing, one pushes. The fitness prefers **inline** (+0.008 over push, +0.019 over dialog) — and dialogs cost a phone fork. |
| **Do the five destinations stay a rail vs a list?** | Same evidence as the first row; the five destinations themselves are not in question, only the region that holds them. |

---

## 8. Is further scoping worth it?

Per the brief, installing was to be conditional on the initial estimated fitness being worth it.
**Recommendation: no further installs.** The structural question is answered — the rail is unusual,
side-by-side exists once, and width and verdict are independent — and the one app whose layout
mattered most was measured live at the exact target window size, which is better evidence than an
install would have produced. The residual gaps are HomeBank (one row, blocked by a tracking-consent
wall) and Skrooge's dock defaults, neither of which moves any ranking.

The one thing an install *would* settle that this survey cannot: whether a 240 px data sidebar
carrying live balances reads as calm or as busy at 1100 px in Sci-Fin's own palette. That is a
judgement about Sci-Fin's voice, and it would be better answered by drawing it as a sixth candidate
in `desktop-options.html` than by installing someone else's app.

---

## 9. Sources

- Actual Budget — [actualbudget.org](https://actualbudget.org/), [github.com/actualbudget/actual](https://github.com/actualbudget/actual), and the public demo at [demo.actualbudget.org](https://demo.actualbudget.org/) (measured)
- GnuCash — [Main Window Areas, manual v5](https://www.gnucash.org/docs/v5/C/gnucash-manual/gui-windows.html)
- KMyMoney — [Your budgets, KDE docs](https://docs.kde.org/trunk_kf6/en/kmymoney/kmymoney/details.budgets.create.html)
- Money Manager Ex — [mmex.doc Getting Started](https://github.com/moneymanagerex/mmex.doc/blob/main/01_Getting_Started.md)
- Skrooge — [The Skrooge Handbook](https://docs.kde.org/trunk_kf6/en/skrooge/skrooge/index.html)
- Firefly III — [budgets how-to](https://docs.firefly-iii.org/how-to/firefly-iii/finances/budgets/), [issue #5352 on bar colours](https://github.com/firefly-iii/firefly-iii/issues/5352)
- Maybe — [github.com/maybe-finance/maybe](https://github.com/maybe-finance/maybe) (no longer maintained)
- HomeBank — [gethomebank.org](https://gethomebank.org) (layout **not** verified; see §1)

---

## Appendix — the scorer, so the numbers can be disagreed with

The survey table as scored, and the six components, are reproduced below. Re-running requires the
optimization engine only for the search; the exhaustive enumeration is plain Python.

```python
SURVEY = {
    #            shell             panes coequal other_tabs             editor    verdict      live   ev
    "actual":   ("data_sidebar",   2,    "yes",  "single_pane",         "inline", "two_state", True,  1.0),
    "gnucash":  ("tab_notebook",   1,    "no",   "single_pane",         "push",   "none",      True,  0.8),
    "kmymoney": ("data_sidebar",   3,    "no",   "reserved_right_pane", "inline", "none",      True,  0.8),
    "mmex":     ("data_sidebar",   2,    "no",   "single_pane",         "dialog", "none",      True,  0.7),
    "skrooge":  ("dock_workspace", 2,    "yes",  "free_second_list",    "dialog", "none",      True,  0.6),
    "firefly":  ("data_sidebar",   1,    "no",   "single_pane",         "dialog", "two_state", True,  0.7),
    "maybe":    ("data_sidebar",   2,    "no",   "reserved_right_pane", "push",   "none",      False, 0.5),
}

def spec_conformance(v, context=None):          # SS2.1 is a prohibition, not a preference
    if v["verdict_colour"] != "none": return 0.0
    return {"icon_rail":1.0, "data_sidebar":1.0, "tab_notebook":0.35, "dock_workspace":0.20}[v["shell"]]

def figures_density(v, context=None):           # SS2.2/SS2.4 calm register
    n = 0
    if v["coequal_budget"] == "yes":  n += 1    # Budget only
    if v["other_tabs"] == "free_second_list": n += 4   # every other tab too
    if v["shell"] == "data_sidebar":  n += 1    # balances beside whatever is shown
    return {0:1.0, 1:0.72, 2:0.50}.get(n, 0.18)

def width_use(v, context=None):
    b = {"single_pane":0.40, "reserved_right_pane":1.00, "free_second_list":0.85}[v["other_tabs"]]
    if v["coequal_budget"] == "yes": b = min(1.0, b + 0.18)
    if int(v["budget_panes"]) >= 3:  b = min(1.0, b + 0.05)
    return b

def prior_art(v, context=None):                 # evidence-weighted match over the survey
    tot = wt = 0.0
    for row in SURVEY.values():
        w = row[7] * (1.0 if row[6] else 0.45)
        hits = (row[0]==v["shell"]) + (row[2]==v["coequal_budget"]) \
             + (row[3]==v["other_tabs"]) + (row[4]==v["editor"])
        tot += w * hits/4.0; wt += w
    return tot/wt

def phone_fork_cost(v, context=None):           # each divergence is code that exists twice
    f = 0
    if v["coequal_budget"] == "yes": f += 1
    if v["other_tabs"] in ("reserved_right_pane","free_second_list"): f += 1
    if v["shell"] in ("tab_notebook","dock_workspace"): f += 2
    if v["editor"] == "dialog": f += 1
    return max(0.0, 1.0 - f*0.22)

def unwritten_deps(v, context=None):            # SS4.5's Learn bodies are not written
    return 0.55 if v["other_tabs"] == "reserved_right_pane" else 1.0
```

Weights: `spec_conformance` 0.30, `figures_density` 0.20, `prior_art` 0.20, `width_use` 0.15,
`phone_fork_cost` 0.10, `unwritten_deps` 0.05.


---

## 10. Second pass — broader scope, engine-scored (2026-09-23, later)

The first pass was too narrow, and one of its counts was wrong. This pass adds apps, adds a
measured instance, and corrects a category error.

### 10.1 A category error in §2: "data sidebar" conflated two different things

§2 recorded "five of seven use a data sidebar". That collapsed two shells that behave very
differently, and the distinction is the whole cost of option F:

| Shell | What sits in the left column | Apps | Register cost |
|---|---|---|---|
| **Icon rail** | five icons, no text, no data | **none** | none |
| **Text-nav sidebar** | named destinations only, no figures | Fava (**160px, measured**), Money Manager Ex, Firefly III, Maybe | none |
| **Data sidebar** | destinations **plus live balances** | Actual (**240px, measured**) | figures on every tab |
| **Tabbed notebook** | no left column; opened tabs | GnuCash | none |
| **Dockable panels** | user-arranged | Skrooge | user's choice |

**The corrected finding:** only **one** surveyed app puts standing figures in the left column.
Option F as drawn copies Actual specifically, not the field. The field's actual consensus is
much weaker than "use a data sidebar" — it is only "do not use an icon rail", which still holds
at **0 of 9**.

### 10.2 Fava (Beancount), measured at 1100×760

- `<aside>` **160px**, 16 named destinations (Income Statement, Balance Sheet, Trial Balance,
  Journal, Query, Holdings, Commodities, Documents, Events, Statistics, Editor, Import, Options,
  Help), text only.
- Single main pane: chart above, account tree table below.
- **Every figure on the page renders in one colour, `rgb(204,204,204)`.** A full double-entry
  accounting UI, negatives everywhere, and no valence colour on any number.

**Correction (same day, after review): the sentence that stood here over-claimed.** It read
"the second independent counterexample to §4.8's conflation of density with the scoreboard … it
carries no verdict at all". That is wrong, and the error was one of scope: the measurement
behind it queried `td`/`span` figures and silently excluded the chart, which is SVG.

Re-measured properly:

- **Figures carry no valence.** Negatives still render `rgb(204,204,204)`, the same as
  everything else. Sign gets no colour. That part holds.
- **The chart carries 47 distinct fills spanning the whole hue circle (0–353°)**, 7 of them in
  the red band and 5 in the green. They are *category* identifiers, not a good/bad encoding —
  there is no valence pairing among 47 hues — but a 47-colour chart breaches §2.5's
  one-accent-per-theme outright, so it is no model for this app either way.
- **The verdict is in the language, not the colour.** The screen is titled *Income Statement*
  and leads with *Net Profit −103,335.53*, beside *Balance Sheet* and *Trial Balance*. That is
  performance-accounting register: a screen whose job is to tell you whether you did well.
  §2.2/§2.4 rule out exactly this framing, and no amount of neutral colour rescues it.

**So Fava is not a register counterexample.** It supports one narrow claim only — *sign need not
be coloured* — and on register it is closer to an example of what §4.8 feared than a rebuttal
of it.

**This weakens the load-bearing claim of §4, and that should be said plainly.** "Width and
verdict-colour are independent" now rests on KMyMoney alone, and KMyMoney is documentation
evidence: its handbook does not *describe* colour coding, which is not the same as observing
that it has none. The claim is not disproven — Actual's red/green is still demonstrably a method
choice inherited from YNAB rather than a consequence of layout width — but it is not
established either, and it was used to justify accepting E's density. Treat it as an open
question, not a finding.

### 10.3 Also scoped, documentation only

- **OpenBudgeteer** — .NET Core + Blazor Server, explicitly bucket/envelope budgeting after YNAB
  and Buckets. The closest licence-free analogue to Sci-Fin's envelope model after Actual.
- **Firefly III** — its public demo was **not** measured: it requires authenticating with a
  password, which is out of bounds even for a published demo account. Its layout claims here
  remain documentation-level, and its red→orange overspend bars plus green "left to spend" box
  are from the docs and issue tracker, not observation.
- **HomeBank** — still unverified; see §1.

### 10.4 Engine scores, replacing the hand-rolled fitness

The first pass scored the options with scorers written by hand for the occasion. This pass runs
the purpose-built engines instead, wired to the full HS+DE portfolio (`designkit status` reports
`mode portfolio`, not the local-search fallback).

**`designkit register`, Budget screen, tone 0.35, referent `fixed-target`, medium web:**

| Budget variant | pattern the engine chose | register_fit | medium_fit | fitness |
|---|---|---|---|---|
| E — both views, 7 sections | f-pattern | **0.835** | 0.965 | 0.851 |
| A/B/D — envelopes only, 4 sections | ambient-reflection | 0.955 | 0.830 | 0.859 |
| F — sidebar + categories, 5 sections | **habit-loop** | 0.955 | 0.830 | 0.859 |

Two results worth keeping. First, the engine independently reproduces §2.4's penalty on E
(0.835 against 0.955) — the hand-rolled `figures_density` term was pointing the right way — but
E recovers it on `medium_fit`, and the net gap is **0.008**, another tie. Second, **only F lands
on `habit-loop`, which is the pattern §4.2 itself assigns to Budget**; E scores as an f-pattern
and the single-view options as ambient-reflection.

**`craftkit ui`, per frame.** This found one real defect — *in the mockup, not the app*. The
sheet's `--txt2` had drifted to `#6B6B70` where `Tokens.kt` ships `66666B`; the drifted value
fails WCAG AA on `surf2` at 4.44:1 and the real one passes at 4.78:1. Fixed, and every token in
`desktop-options.html` now matches `Palettes.slateLight` exactly. It matters beyond tidiness:
every fill, atom count and contrast figure in this document was measured off that artefact.

After the fix, **frame E is the cleanest of the six** — 0 errors, and the tightest system: three
spacing values and four radii, against eight and six for A and F.

A second craftkit flag, `txt3` contrast at 2.4–3.3:1, is a **non-finding**: §2.5 already confines
`txt3` to placeholders and inactive text, and `TokenContrastTest.kt` enforces it with a
whitelist of exactly two call sites. WCAG exempts placeholders.


---

## 11. KMyMoney installed and walked, every screen (2026-09-23)

KMyMoney 5.1.3 was installed from `download.kde.org` via winget (SHA-256 verified), driven at
**1100 x 760**, walked across all its destinations, and uninstalled. Sections 2 and 4 had described
it from documentation and from its Budgets view alone. **That was not enough, and it produced the
wrong conclusion.**

Note on method: Qt paints its widgets inside a single native window, so `EnumChildWindows` returns
no child rectangles. Pane figures below are measured from the screenshot with the window's own
1100/815 scale factor, +/-3 px -- weaker than the DOM measurements used for Actual and Fava, and
labelled as such.

### 11.1 Seven destinations, at least six different layouts

| Destination | Structure |
|---|---|
| **Inizio** (home) | scrolling dashboard; stacked sections; two columns *inside* a section |
| **Conti** (accounts) | single full-width tree, filter bar along the bottom, one aggregate bottom-right |
| **Categorie** | **identical shape to Conti**, different data |
| **Transazioni pianificate** | single full-width table, a "new" button top-left, filter top-right, search field |
| **Registri** (ledger) | list on top, **persistent inline editor strip at the bottom**, tabbed by entry kind (Aumenta / Trasferimento / Riduci) |
| **Budget** | three panes -- list 427 | (tree 282 over assignments 235) 531, sidebar 107 |
| **Report** | single tree of report types; each result **opens in a new tab** |
| **Previsione** (forecast) | parameter strip on top, **tabbed results** below, split into two columns |
| **In uscita** (outbox) | table plus a **vertical action-button column on the right** |

**The finding is that there is no shell discipline.** Lists, editors and width are re-decided on
every screen. Judged from Budget alone the app looks like a considered three-pane design; walked in
full, that three-pane view is one of seven ad-hoc answers, not a system.

### 11.2 The main lesson is negative, and it argues for a stated rule

This is what an app looks like when the layout rule is never written down once: every destination
re-invents it, and the result reads as dated for reasons beyond widget style. It is an argument for
SS4.8 committing to an explicit rule about what the desktop width is for -- which is what C's
"reservation rule" was reaching for, even though C's own proportions were wrong (SS10.4, and the
list-pane point below).

### 11.3 The proportions on Budget are wrong in an instructive way

The budget *list* -- which for nearly every user holds one or two rows -- takes **427 px, 39% of the
window**, while the entire working area gets 531 px and must split three ways vertically. Width
allocated by *category of content* ("this is a list pane") rather than by *quantity of content*.
That is the same error as scoring a reserved pane at a flat 1.00 without measuring its fill.

### 11.4 One positive idea, and it resolves an open question under E

`Registri` puts the editor in a **bottom strip beneath the list**, permanently, with tabs for the
kind of entry. That is a fourth answer to "where do editors go", alongside push, dialog and right
pane -- and it fits **E** specifically. E consumes both *columns* on Budget but leaves the vertical
axis entirely free, so a goal or category editor can occupy a bottom strip without disturbing either
column. This directly answers the open item recorded as "both columns are taken, so push or dialog".

A second, smaller idea from the Budgets view: the `Periodo` radio group (Mensile / Annuale /
Individuale) reshapes the editor beneath it, so one category's budget is entered as one monthly
figure, one yearly figure, or twelve individual months in the same panel. That is a *feature* idea
rather than a layout -- Sci-Fin's Budget has no notion of per-month variation -- so it does not bear
on A-F.

### 11.5 What is not worth taking

The twelve-destination sidebar that scrolls because it cannot fit its own contents; the Budget
list-pane proportions; the dialog-dense editing. The owner's judgement on first sight -- "looks
absolutely dated, if there is anything in its structural organization worth keeping, it should be
chosen carefully" -- is borne out: exactly two ideas survive the walk, one of them a feature rather
than a structure.


---

## 12. Actual Budget walked, every screen (2026-09-23)

The same walk KMyMoney got in §11, run against the modern analogue. Nine screens, driven at
**1100 x 760** in the public demo and measured from the DOM.

### 12.1 One shell, nine screens

| Screen | sidebar | main | inner measure | figure-ish leaves |
|---|---|---|---|---|
| Budget | 240 | 860 | table ~636 **centred** | 80 |
| Reports | 240 | 860 | widget grid 829, scrolls to **1195** | 27 |
| Schedules | 240 | 860 | 820 inset | 15 |
| All accounts | 240 | 860 | 849, scrolls to 5766 | 36 |
| Account register | 240 | 860 | 849, scrolls to 6324 | 36 |
| Payees | 240 | 860 | 820 inset | 11 |
| Rules | 240 | 860 | 820 inset | 15 |
| Tags | 240 | 860 | 820 inset | 11 |
| **Settings** | 240 | 860 | **530 centred** | 12 |

**The shell never changes.** 240 / 860 on all nine. Against KMyMoney's seven screens and six
improvised layouts, this is the clearest structural contrast in the whole survey.

The sidebar is **fixed, not proportional**: when the viewport was accidentally reduced to 815 px
mid-walk, the sidebar stayed at exactly 240 and the main pane absorbed the entire loss.

### 12.2 The rule is "one shell, measure varies by content type"

The inner measure is not arbitrary, and it is not constant either:

- **Tables** take near-full width -- 820 to 849 of the 860 available.
- **The budget table** takes ~636, centred, even though 849 is available.
- **Settings** takes **530, centred** -- a reading measure, not a table measure.

So Actual answers "what is the width for?" **once, per kind of content**, rather than per screen.
That is the stated rule KMyMoney never wrote down, and it is a third formulation that neither C nor
E proposed: not *reserve a pane*, and not *pair two views* -- **fix the shell and vary the measure**.

Fava independently lands in the same family (160 px text-nav sidebar, one main pane), which makes
this the modal answer among the apps that are actually pleasant to use.

### 12.3 Editing is inline, in the cell

Clicking a category cell in the register turns **the cell itself** into a combobox, with a
contextual dropdown listing the categories and their current balances, plus "split transaction".
No dialog, no push, no side pane, and not KMyMoney's bottom strip. Across the survey there are now
four observed answers to "where do editors go":

| Answer | Seen in |
|---|---|
| inline in the cell | Actual |
| persistent bottom strip under the list | KMyMoney (Registri) |
| modal dialog | KMyMoney (most other screens), Money Manager Ex |
| push to a detail screen | Sci-Fin's phone build |

### 12.4 What this means for the options, stated without reopening the decision

E is the owner's decision and stands. But the evidence should be recorded straight: **E's two
columns are a departure from what both modern apps do.** Under the "one shell, varying measure"
rule, Budget would be a single main pane with SS4.3's segmented control kept, and the width would be
spent by giving the budget table a wider measure -- which is closer to A or B than to E.

Two things follow that do *not* require reopening anything:

1. **The measure rule is adoptable under E as well.** Whatever the column structure, "tables get a
   wide measure, prose and settings get ~530 centred" is a rule Sci-Fin can state once in SS4.8 and
   apply everywhere. It is the part of C worth keeping after C itself was withdrawn.
2. **Inline editing is available to E**, and is what Actual does with a comparable amount of
   content, which strengthens the SS11.4 conclusion that SS4.8 rule 3 (sheets become dialogs)
   should lapse.

### 12.5 Note on the Settings measure

530 px centred inside 860 is worth singling out: it is almost exactly candidate **B**'s answer
("the width buys nothing, deliberately"), applied to one screen rather than to the whole app.
B was scored as a whole-app strategy and finished mid-table; as a *per-content-type* rule it is what
the best-regarded app in the survey actually ships.


---

## 13. GnuCash 5.16 and Money Manager EX 1.9.4, installed and walked (2026-09-23)

Both installed from their official GitHub releases via winget (SHA-256 verified), driven at
**1100 x 760**, walked, and removed. Both are current releases, unlike KMyMoney's 2022 build.

### 13.1 GnuCash 5.16 -- one shell, per-tab toolbars

| Tab | Structure |
|---|---|
| **Conti** | full-width account tree (Nome / Descrizione / Totale); **summary bar** at the foot: Totale complessivo, Attivita nette, Profitti |
| **Register** (double-click an account) | full-width ledger (Data / Num / Descrizione / Trasferimento / R / Aumento / Diminuzione / Saldo); **first row is an inline editable blank entry**; summary bar: Attuale, Futuro, Compensato, Riconciliato, Minimo previsto |
| **Report** | a rendered document, **left-aligned at roughly 470 of 1084 px**, no centring; **no summary bar** |

**No sidebar anywhere.** One full-width pane per tab, and tabs accumulate -- Conti, then the
register, then each report, each closable.

**The per-screen difference is carried by the toolbar, not the layout.** Accounts: Salva / Chiudi /
Aperto / Modifica / Nuovo / Elimina. Register: Duplica / Inserisci / Annulla / Vuota / Mostra
frazionamento / Pianifica / Trasferimento / Riconcilia. Report: **Indietro / Avanti / Ricarica /
Stop / Opzioni / Esporta / Stampa** -- browser navigation. That is a coherent system: the shell is
constant and the *controls* change, which is a third discipline distinct from Actual's (constant
shell, varying measure) and from KMyMoney's (no discipline).

**Editing is inline in the row**, as in Actual.

**The report tab is the clearest emptiness failure in the survey.** A narrow left-aligned document
in an 1100 px window, with no centring and no second pane -- worse than anything measured in
Actual, and a caution for any option that renders prose or a report into a wide pane without
setting a measure.

### 13.2 Money Manager EX 1.9.4 -- one shell, one reused list template

Measured at 1100 x 760: **Navigatore panel ~197 | workspace ~891** (screenshot measurement, the
panel is dockable and has its own title bar and close button).

| Destination | Structure |
|---|---|
| **Dashboard** | summary strip across the top (Totale Patrimonio Netto / Riconciliata / Titolo / Saldo), then a **two-column grid**: accounts table left, income-vs-expense chart plus a totals table right, "Statistiche Transazioni" beneath |
| **Tutte le Transazioni** | filter strip, table, **bottom action row** (Nuovo / Modifica / Duplica / Elimina / Invio / Salta / attach / search), tip banner at the foot |
| **Conti Bancari** | **the same template again**, identical columns and action row |
| **Pianificatore Budget** | with no budget defined, the empty state is **the in-app manual**, rendered in the workspace with Indietro / Avanti navigation |
| **Resoconto Transazioni** | opens a **large modal dialog** for configuration -- Specifica (account, period, payee, category, tags, status, type, amount range, number, notes, colour, attachments) plus Opzioni di Presentazione (hide columns, group by, chart, combine splits) and a saved-settings dropdown |
| **Resoconti** | a tree node of report types |

**MMEX holds one shell and reuses one list template** across its transaction screens, which is
closer to Actual's discipline than to KMyMoney's improvisation. Its editing is **dialog-based**
(Nuovo / Modifica open modals), and its report configuration is a modal too.

Two details worth noting. The dashboard chart legend uses **green for Entrate and red for Uscite**
-- categorical, but read as valence. And the **manual-as-empty-state** is a genuinely unusual idea:
the screen that has nothing to show teaches you what it is for, in place.

### 13.3 The four desktop apps, compared on discipline

| App | Shell | What varies per screen | Editing |
|---|---|---|---|
| **Actual** | fixed 240 / 860 sidebar + main | the **inner measure** (849 tables, 636 budget, 530 settings) | inline in the cell |
| **GnuCash** | tabbed notebook, one full-width pane | the **toolbar** | inline in the row |
| **Money Manager EX** | nav panel ~197 + workspace ~891 | little -- one list template reused | modal dialogs |
| **KMyMoney** | none held constant | **the entire layout** (six across seven screens) | dialogs, plus one bottom strip |

Three of the four hold a shell constant and vary exactly one thing. KMyMoney varies everything.
That is the survey's clearest structural result, and it is an argument for SS4.8 naming the constant
and naming the one thing allowed to vary -- whichever option is chosen.

---

## 14. The decided layout drawn, and what measuring it found (2026-09-23/24)

Sections 1--13 surveyed other apps. This section records what happened when the
survey's conclusion was drawn as Sci-Fin rather than as a comparison, and the three
measurements that produced. **Nothing here is a decision** -- §4.8 in the spec is
still untouched, and the rail question below is still open with the owner.

> **Withdrawn in framing, 2026-09-24, by §15.** Everything in §14 is drawn and measured
> at a single window size, as though 1100 x 760 were the size the app has. It is not --
> the window is resizable and §2.5 already says so. **Every figure below is therefore one
> sample of a rule, not a constant**: `506 | 505` is what "divide the content in two"
> produces at one width, and `530 centred` is what "hold a measure and centre it"
> produces at one width. §15.4 gives the rules the figures are samples of. The
> measurements themselves stand -- they were taken correctly -- and the comparisons
> between options stand, because every option was sampled at the same size. What does
> not stand is treating any of these numbers as the answer.

Artefacts: `design/desktop-inscope-2026-09-23.html` (88px rail) and
`design/desktop-inscope-sidebar-2026-09-23.html` (240px sidebar). Same six frames,
same content, same fixture figures.

### 14.1 The five destinations, drawn under one stated rule

§4.2 fixes five destinations, so six frames: Budget, Budget-while-editing, Activity,
Learn, Reports, Settings. Content is the build's own strings, read out of
`LearnScreen.kt`, `ActivityScreens.kt`, `ReportsScreen.kt` and `SettingsScreens.kt`
rather than invented or taken from the prototype (§14 of the spec forbids the latter).

The rule the frames are drawn under is the one §13.3 argues for -- name the constant,
name the one thing that varies, name the exceptions:

- **Constant:** the shell. Rail plus one content area.
- **Varies:** the *measure*. Tables and charts take the full content width; prose and
  settings take **530 centred**. That is Actual's rule, measured in §12.2.
- **One named exception:** Budget splits into two columns, the only destination in
  §4.2's table with two co-equal views. Naming it as an exception is what stops it
  becoming a precedent -- which is exactly what §11.1 shows going wrong in KMyMoney.
- **Editors:** the bottom strip taken from KMyMoney's Registri (§11.4), which is the
  one positive idea that walk produced. It resolves the open question of where an
  editor goes when both of Budget's columns are taken: E consumes the horizontal axis
  and leaves the vertical one free, so the editor costs neither column and needs no dialog.

Verified by DOM measurement, both files: rail and content constant across all six
frames, the 530 measure exact on Learn and Settings, editor strip 140px, **zero
clipped elements**.

### 14.2 The same six with a 240px sidebar, and what it costs

§7's first open sub-decision was the rail: zero of nine surveyed apps use an icon rail.
Rather than argue it, the sheet was duplicated with the rail replaced by a 240px named
sidebar. The CSS class name was deliberately kept, so **the six navigation blocks are
byte-identical between the two files** -- `diff` reports zero changed lines containing
the nav markup. Only the rule behind them differs. That is the claim "nothing else in
the layout would change", carried out rather than asserted.

| | 88px rail | 240px sidebar |
|---|---|---|
| Shell | 89 / 1011 | **240 / 860** -- exactly Actual's measured pair (§3) |
| Budget | 506 \| 505 | 430 \| 430 (860 halves evenly; 1011 does not) |
| Learn, Settings | 530 at x=330 | 530 at x=405 |

The sidebar takes **151px** from the content area. It does not fall evenly, because
the measure rule already decides how much width each destination uses:

| Destination | Cost |
|---|---|
| Learn | **no width** -- 530 either way; shifts 75px right |
| Settings | **no width** -- 530 either way; shifts 75px right |
| Budget | -76 and -75, one per column |
| Activity | -151 across five table columns |
| Reports | -151, absorbed by the bars |

Two of five do not lose any width at all. What the 151px buys is that the five
destinations are readable words rather than glyphs, permanently, with no hover.

### 14.3 Fill does not decide this, measured in both directions

"Fill the space" produced three wrong recommendations earlier in this survey, so it
was measured rather than assumed. Both files scored with **one identical script** --
leaf elements only, their area over the body's area -- so the columns are comparable
to each other and to nothing else.

| Destination | 88px rail | 240px sidebar | Change |
|---|---|---|---|
| Budget | 33.9% | 32.8% | -1.1 |
| Budget, editing | 37.4% | 36.2% | -1.2 |
| Activity | 57.7% | 56.6% | -1.1 |
| Reports | 26.3% | 25.2% | -1.1 |
| Learn | 16.1% | 17.7% | **+1.6** |
| Settings | 12.6% | 13.6% | **+1.0** |

Nothing moves more than 1.6 points, and the three *wide* screens move the "wrong" way
-- slightly emptier, not denser. The reason is that almost every element on these
screens is a stretch-width block: narrow the pane and the cards, bars and rows narrow
with it, so the proportion covered barely changes. Only Learn and Settings behave as
intuition predicts, and only because their 530 measure is the one thing that does not
stretch. **Fill is not an argument here in either direction.**

(These figures were re-measured after §14.5 raised the client area from 726 to 760.
Every figure moved; every *delta* held to within 0.2. The conclusion does not depend
on getting the window height right.)

### 14.4 The measurement that rules out a 160px sidebar

Fava's sidebar is 160 (§10.2) and would have cost 71px instead of 151. It is ruled out,
on arithmetic rather than taste.

§2.5 puts the type ramp on the OS axis and `TextScale` runs **SM 0.875 to XL 1.5**
(`model/Enums.kt:73`). The glyph and the padding are on the *screen* axis, so they do
not grow with the text: every bit of text growth comes out of the label's budget.
Available label width, read off `Frame.kt:226-236` -- the rail gives **76dp** (88 less
`u/2` twice for the column and `u/4` twice for the item); a sidebar row gives its width
less 81dp of border, padding, 20dp glyph and gap, so **79dp at 160** and **159dp at 240**.
The longest label is "Settings".

| TextScale | needs (xs / base) | Rail 88 -- has 76 | Sidebar 160 -- has 79 | Sidebar 240 -- has 159 |
|---|---|---|---|---|
| SM 0.875x | 44 / 52dp | fits | fits | fits |
| MD 1.0x | 50 / 59dp | fits | fits | fits |
| LG 1.25x | 63 / 74dp | fits | fits | fits |
| XL 1.5x | 75 / 89dp | fits by **1dp** | **does not fit** | fits, 70dp spare |

160 is the only one of the three that breaks inside the app's own range, and it breaks
on "Settings" -- a destination everyone visits, and a single word, so wrapping is not
available as a rescue. Compose would break it inside the word.

**Two limits on this table, stated rather than buried.** The widths are canvas
measurements in Segoe UI, which is what Compose Desktop resolves to on Windows but is
not the same rasteriser -- so the rail's **1dp of headroom at XL is inside measurement
error** and needs confirming against the running app. And that 1dp is the rail's entire
margin: step 8's queued "System" text-size default would let the OS drive the factor
past 1.5 (Windows offers up to 2.25x), and above roughly **1.52x** the 88px rail's
labels break within the word too. Of the three shells, only 240 has room for a change
that is already on the plan.

### 14.5 The window itself -- a drawing error, and a real one underneath it

Both sheets wore **macOS traffic lights** until 2026-09-24. That was a habit, not a
decision, and it was wrong: `desktopApp/.../Main.kt:57` opens a plain decorated
`Window(title = "Sci-Fin")` with no `undecorated`, so the app wears the native Windows 11
titlebar -- icon and title left, minimise/maximise/close right. Corrected in both files
at the height measured on this machine with `GetSystemMetrics`: `SM_CYCAPTION` 23 +
`SM_CYSIZEFRAME` 4 + `SM_CXPADDEDBORDER` 4 = **31px**.

Correcting it surfaced something worth more than the styling, and this one is a **code
finding, not a design one**:

> `rememberWindowState(width = 1100.dp, height = 760.dp)` sets AWT's **outer** bounds,
> decoration included. Every sheet in this series -- including sections 1--13 -- has
> treated 1100 x 760 as the **client** area. The running app therefore has less room
> than every frame assumes: at least 31px vertically, plus whatever the DWM resize
> frame takes at the sides.

What it does and does not touch:

- **No comparison in this register changes.** Every option was drawn under the same
  assumption, so the scoring in §5, the rail-versus-sidebar cost in §14.2 and the fill
  deltas in §14.3 are all unaffected.
- **Absolute claims are provisional**, including "content 860, exactly Actual's 860"
  (§14.2). If the client area is really 1084 wide, a 240px sidebar leaves 844 and that
  correspondence evaporates.

### 14.5.1 Measured, 2026-09-24: the client area is 1084 x 721

Settled without building or launching the app. `ComposeWindow` extends `JFrame` and
`rememberWindowState(w, h)` maps to `setSize()`, so a plain decorated `JFrame` at
1100 x 760 under the same JDK reports the same decoration insets. `addNotify()` creates
the native peer -- which is what populates the insets -- without `setVisible()`, so no
window ever appeared and nothing persisted. JDK 21.0.11, display scale 1.0x.

```
insets   top 31   bottom 8   left 8   right 8
CLIENT AREA           1084 x 721      (lost 16 horizontally, 39 vertically)
to get 1100x760 of client, setSize would need 1116 x 799
```

The top inset is **31**, matching the `GetSystemMetrics` sum in §14.5 exactly. The 8px
side and bottom insets are the DWM resize frame. They are largely invisible on screen,
but they are real to *layout*: the content pane genuinely gets 1084 x 721, whatever the
frame paints.

**Three consequences, two of which retract claims made earlier in this register.**

1. **§4.8's own arithmetic does not fit.** The spec says the window is 1100dp and §4.8
   splits it into "an 88dp rail, a 368dp list column and a 644dp detail column" --
   which sums to exactly 1100. Only 1084 exists. `Frame.kt:117` gives the detail pane
   `weight(1f)`, so it absorbs the entire shortfall silently: the real detail column is
   **628, not 644**. Nothing ever reported this because a weighted pane cannot fail.
   This is a defect in the spec's stated numbers against the shipped code, and it is
   independent of the rail-versus-sidebar question.
2. **"240 / 860, exactly Actual's measured pair" is withdrawn.** The real content area
   behind a 240px sidebar is **844**. The correspondence with Actual's 860 was an
   artefact of assuming 1100 of client; it does not survive the measurement. 240 is
   still the width Actual uses, and §14.4 still rules out 160 -- neither of those
   depended on the 860.
3. **"860 halves evenly" is withdrawn too.** 844 does not: re-derived it is 422 | 422,
   and as currently drawn (a fixed 430 beside a flexed sibling) it renders **430 | 414**.
   The rail sheet degrades the same way, 506 | 489 against a 995 content area.

**What survives unchanged.** Every *comparison* in this register: all options were drawn
under the same assumption, so §5's scoring, §14.2's 151px cost and §14.3's fill deltas
are unaffected. And re-measuring both sheets forced to 1084 x 721 found **zero clipped
elements** across all twelve frames -- the content fits the real window; only the
absolute widths were wrong.

**Still owed:** §14.4's 1dp of headroom at XL. That one genuinely needs a Compose
render, and is better answered as a test that asserts the nav labels fit at every
`TextScale` than as a one-off look -- step 8 is about to move the ceiling.

**Open decision, for the owner.** Two ways to reconcile the 16 x 39:
(a) set `rememberWindowState` to **1116 x 799**, delivering the 1100 x 760 of client
every design number in this register assumes -- the window grows slightly on screen;
(b) keep the window at 1100 x 760 and **re-derive** §4.8's columns from 1084 x 721 --
the spec's 88 / 368 / 644 becomes 88 / 368 / 628, and every mockup figure shifts.
Not decided here.

### 14.6 What is still not recorded, and why

**`scifinspec.md` has not been touched by any of this.** It carries zero dated entries
for 2026-09-23 or -24 on §4.8. Its live text still says §4.8 "is being reopened"
(§2.5's desktop paragraph) and still records the *old* §4.8 as built -- "the rail,
list-detail at 840dp, dialogs at 600dp, Reports single, the 52ch measure cap" (§13.3
of the spec). Everything this survey concluded is superseding text that still stands.

That is correct for now and should not be fixed quietly. The standing rule is that a
visible decision is settled with the owner and recorded with a dated lead-in *before*
it is coded, and **the rail question is still with the owner**. Writing §4.8 now would
record a decision nobody made.

When it is settled, the write-back is: §4.8's body (the constant, the one thing that
varies, the named exception, the editor strip); a strike through the adaptive-reflow
conclusion, keeping its register argument; §2.5's desktop half of the scale rule, which
§4.8 was blocking; the density cost accepted knowingly with §2.1 as the safeguard; the
§13.3 decided-but-unbuilt entry and the §13.4 trail. Then `tools/speccheck.py` and
`tools/selftest.py`, both clean.

**Also owed, and independent of the decision:** §4.8 rule 3 -- sheets become dialogs at
600dp -- should lapse. Inline editing is 2 of 4 surveyed apps (Actual in-cell §12.3,
GnuCash in-row §13.1), plus KMyMoney's bottom strip, and §14.1 adopts the strip.

---

## 15. How shipping apps actually behave when the window is resized (2026-09-24)

Sections 1--14 all asked "what should the layout be at 1100 x 760". That question was
wrong, and the owner said so: *"There's not supposed to be numbers set in stone, the
user can always re-adjust the window to any size."* §2.5 already says every measurement
is continuous in window width; every sheet in this register is a fixed-pixel drawing at
one size. This section replaces the question with the right one -- **what are the rules
that hold at every size** -- and answers it by measuring apps installed on this machine.

Workflow order came from the meta-optimizer (fully computable, zero model turns):
`sweep > classify > rules > retract > redraw > spec`, fitness 0.950, confirmed as the
unique optimum against all 720 permutations. `cheap_first` at 0.667 is the only
shortfall and is unfixable -- precedence forces the most expensive task first.

### 15.1 Method

Four installed apps with a nav-plus-content shell: **Notion** (named by the owner),
**Obsidian**, **VS Code**, **Todoist**. Each driven through a two-axis sweep by
`MoveWindow`, capturing the client area at every step:

- **Width:** 1920 > 660 > 1920, fifteen steps, height fixed at 900.
- **Height:** 1000 > 480 > 1000, thirteen steps, width fixed at 1200.

Both legs run **down and back up through the same points**, so a reflow threshold and
hysteresis -- a pane that collapses and does not come back -- are separately visible.

Boundaries are found by box-resampling the client area down one axis, so each line is
the mean background colour of that column or row, then locating steps that persist.
Columns are read over the vertical middle 60%; rows over the horizontal right 55%, so a
full-height sidebar does not mask the content area's horizontal bands. **Read the width
phase's columns and the height phase's rows only** -- on each phase the other axis is
sampling different content at every step and is not comparable across steps.

**Privacy.** These are the owner's personal apps. The analyser reads pixels only to find
background boundaries, prints column and row indices and nothing else -- no text, no
crops, no thumbnails -- and unlinks every capture it reads. No content from any of them
entered the transcript. All four were closed afterwards; none were running before.

**Two failures found and corrected, both of which would have produced fiction:**

1. **Cold-app artefact.** The first Notion pass showed different boundaries at 1920 on
   the way down and on the way up, which reads exactly like hysteresis. It was not: the
   sweep began 1.2s after launch, while the app was still painting. Re-run warm, every
   down-leg reading matches its up-leg twin **exactly**. The hysteresis claim was
   withdrawn before it reached the register.
2. **Foreground contamination.** Todoist's first sweep reported `49, 267, 469, 545` --
   Notion's exact signature -- and later `4, 53, 119, 348`, which is VS Code's.
   `SetForegroundWindow` had not raised it, so the capture was of whichever window sat
   at 0,0. Those numbers described other apps. Detected by signature collision, discarded
   entirely, and re-run with every other app closed.

### 15.2 What was measured

Stable boundaries, warm, both legs identical in every row below.

| App | Left chrome | Across width | Across height | Threshold | Reversible |
|---|---|---|---|---|---|
| **Notion** | 49 + **267** | both **fixed** at every width, client 644--1904; content edges 469 / 545 also fixed | rows `23, 63, 212, 260, 450` **identical** at every height, 480--992 | none seen | exact |
| **Obsidian** | 43 rail + **344** | both **fixed**; the remainder splits **exactly in half** | rows 1 / 63 fixed; content centre at **(h − 246) / 2** | none seen | exact |
| **VS Code** | 4 + **53** wide / **74** narrow | chrome **fixed**; content **centred at a 946 max measure** | rows `6, 62, 161, 253, 404` **identical** at every height, 472--992 | between client 984 and 1184 | exact |
| **Todoist** | **280** expanded / **66** collapsed | both **fixed**; sidebar **collapses** when content would fall below ~484 | row `172` **identical** at every height, 441--961 | client **745--764** | exact |

Two of those are arithmetic, not description, and both were checked against every step:

- **Obsidian** puts its content division at `344 + (client − 344) / 2` -- 494, 544, 594,
  664, 764, 864, 964 at client 644, 744, 844, 984, 1184, 1384, 1584. Seven for seven.
  (At 1184 the sidebar edge itself reads 375 rather than 344; the division still lands on
  344's prediction, so the 375 is a detection artefact, not a moved sidebar.)
- **VS Code** places its content block's left edge at `(client − 946) / 2` -- 119, 219,
  319, 479 at client 1184, 1384, 1584, 1904. Four for four. That is a **946px maximum
  measure, centred**: below the threshold the content fills instead (client 984 less 74
  of chrome is 910, under 946, and the pattern changes exactly there).

### 15.3 Three things are unanimous, four apps out of four

1. **Navigation chrome is a fixed pixel width. It never scales with the window.** Not
   one of the four grows its rail or sidebar as the window grows. Notion holds 267 from
   a 644 window to a 1904 one -- 41% of the window at the narrow end, 14% at the wide end.
2. **Top chrome is a fixed pixel height.** Three of four have row boundaries that do not
   move by a single pixel across a 2x height range. Obsidian's move only because its
   content is split vertically; its chrome rows, 1 and 63, are fixed like everyone else's.
3. **Resizing is exactly reversible.** Every app returns to an identical layout at a
   given size regardless of the path taken to it. No app "remembers" having been narrow.

**This is the direct answer to §2.5's open desktop half.** The phone half is settled
(width ÷ 360, floored 0.85). The desktop half was explicitly left waiting on §4.8's
revisit, on the stated grounds that "the width" is three different numbers above 600dp.
The evidence says the question dissolves: **nothing on the desktop should multiply the
chrome by a window factor**, because no shipping app does, and the reason is legible --
a navigation label does not become easier to read because the window got wider.

### 15.4 The variation lives in the content area, by exactly three mechanisms

All four apps hold chrome constant, so all the change is in the content. Between them
they use three mechanisms and no others:

| Mechanism | Attested in | What it does |
|---|---|---|
| **Reveal** | Notion | content keeps a fixed measure; extra width becomes empty space |
| **Divide** | Obsidian | the remainder splits proportionally -- exactly 50/50 |
| **Centre to a max measure** | VS Code | fills below a threshold; above it, holds the measure and centres |

**Those are precisely the three cases Sci-Fin has**, and the mapping is forced by content
type rather than chosen:

- **Budget** -- two co-equal columns -- is *divide*. Obsidian's 50/50 is the same shape,
  and it replaces the fixed `506 | 505` with a rule: `(content − 0) / 2` each.
- **Learn and Settings** -- prose and rows -- are *centre to a max measure*. This is what
  §14 drew as "530 centred", but as a **rule with a threshold**: fill below it, centre
  above it. VS Code's 946 shows the threshold behaviour explicitly; the 530 stays a
  candidate number for Sci-Fin's own ramp, not a borrowed constant.
- **Activity and Reports** -- a table and charts -- are *reveal* or *divide*; a table
  legitimately grows its columns, which is Actual's behaviour from §12.2.

**And the fourth mechanism is collapse -- which is governed by content, not by window
width.** Only Todoist has one, and bisecting it at 20px steps puts it exactly between
client 744 and 764, identically on both legs. Read at a lower detection threshold the
two states are legible: the sidebar is **280px expanded and 66px collapsed**, with its
internal edges at 85 and 130 fixed across the whole expanded range like everyone else's
chrome.

The arithmetic says what the threshold actually protects. At client 764 the content gets
`764 − 280 = 484`. One step narrower, at 744, keeping the sidebar would leave 464 -- and
that is where it yields, restoring the content to `744 − 66 = 678`. **Todoist is not
collapsing at a window width; it is collapsing to protect ~484px of content**, which is
the owner's own reading of it: it collapses when the width becomes too small to keep
showing the main page clearly.

**That is a better rule than a width class**, and it is the one §4.8 should adopt. A
600dp breakpoint names an accident of the window; a protected content minimum names the
thing actually being defended, and derives the breakpoint from it. Sci-Fin's number is
not Todoist's -- it follows from whatever measure §2.5's ramp wants -- but the *form* of
the rule is: **name the content floor, and let the navigation yield to preserve it.**

### 15.5 The one number the sweep did settle, twice

Every app reported a client width of exactly **outer − 16** and, for the three with
custom titlebars, a client height of **outer − 8**. That independently confirms §14.5.1's
JFrame measurement from four unrelated codebases.

**Todoist is the exception and it is instructive:** its client height is `outer − 39`,
not `outer − 8`. Three of the four Electron apps draw their own titlebar and reclaim the
31px; Todoist uses the native one. **Sci-Fin currently uses the native one too**
(`Main.kt:57`, a decorated `Window()`), which is why §14.5.1 found 1084 x 721 inside a
1100 x 760 window. That is a choice, not a given.

### 15.6 What this method could not see

- **Detection finds background changes, not semantics, and the threshold matters.** At
  a step tolerance of 9 Todoist showed only one stable column at 47 -- too narrow to be a
  sidebar -- because its sidebar sits on nearly the same background as its content. Re-read
  at a tolerance of 4, the same captures resolve cleanly into `85, 130, 280, 346`
  expanded and `66` collapsed. **A boundary that does not appear is not a boundary that
  does not exist**, and the first reading of Todoist understated it. Every figure in
  §15.2 for the other three apps was stable at both tolerances.
- **No content was read**, deliberately, so nothing here explains *why* a boundary moved
  -- only that it did, by how much, and whether it was reversible.
- **One machine, one display scale (1.0x), one theme each.** Nothing here establishes
  behaviour under OS display scaling, which is a separate axis from both window size and
  text size.

---

## 16. Scoring the policies, and the two decisions (2026-09-24)

§15 measured what four apps do. This section scores those behaviours as policies for
Sci-Fin, and records what the owner settled.

### 16.1 Six policies, scored on four computable components

Every input traces to §15's measurements or to a count in `ui/`; no constant is asserted.
The components: **coverage** (fraction of the five destinations whose content type the
policy serves), **prior_art** (mean attestation across the four apps), **floor_safety**
(does narrowing protect a measure, or just squeeze), **low_churn** (destinations whose
layout code changes).

| Policy | Total | coverage | prior_art | floor | churn | Wins across all weightings |
|---|---|---|---|---|---|---|
| **vscode** -- measure + reveal | 0.776 | 0.80 | 0.62 | 1.00 | 0.60 | **43.3%** |
| **hybrid** -- all three | **0.785** | 1.00 | 0.50 | 1.00 | 0.40 | 31.1% |
| **scifin_now** -- status quo | 0.610 | 0.60 | 0.50 | 0.50 | 1.00 | 23.1% |
| todoist -- floor only | 0.370 | 0.00 | 0.00 | 1.00 | 0.80 | 2.5% |
| notion | 0.526 | 0.80 | 0.62 | 0.00 | 0.60 | **0%** |
| obsidian | 0.455 | 0.60 | 0.50 | 0.00 | 0.80 | **0%** |

**The sensitivity reverses the headline.** My weights put `hybrid` first by 0.009, but
swept over all 1771 weightings summing to 1 at a 0.05 step, `vscode` wins 43.3% against
`hybrid`'s 31.1%, and head-to-head `hybrid` wins only 31.7%. **No claim is made that
`hybrid` won.** What the scoring does settle is that Notion's and Obsidian's policies win
in *zero* weightings, because neither protects a content measure when the window narrows.

**One confound, stated rather than buried.** Each app's mechanism set is what it was doing
in the single session measured. Obsidian happened to have a split pane open; VS Code did
not. VS Code supports splits, so its missing `divide` may be session state rather than
policy. That weakens `prior_art` specifically -- the component that penalised the split.

### 16.2 Decided: prose and settings cap at a measure and centre

**(Decided by the owner, 2026-09-24.)** Learn and Settings hold a maximum measure and
centre above it, filling below it. Attested in VS Code (946) and Actual (530). This is
the first half of §4.8's rewrite and it is settled; the measure's *value* is not, and
should come from §2.5's ramp rather than be borrowed.

### 16.3 Open: whether Budget divides

Drawn both ways at three widths in `design/budget-split-2026-09-24.html`, identical
content in all six frames (8 cards each), verified by DOM measurement.

| At client | A -- divide | B -- one column, capped |
|---|---|---|
| 700 | one column at 611, **145px** scroll | one column at 530, 81 empty, **125px** scroll |
| 1084 | 497 / 498, **0px** scroll, 0 empty | 530, **125px** scroll, 465 empty |
| 1800 | 855 / 856, **0px** scroll, 0 empty | 530, **125px** scroll, 1181 empty |

**A correction to this sheet's first draft.** It marked the overflow with a red "below the
fold" line, which implied content was lost. It is not -- both options scroll, as every app
in §15 does, and the last category is reachable in all six frames. Measured properly the
cost of B is **125px of scrolling, about two cards, and constant**: widening from 1084 to
1800 adds 716px of empty space and removes none of it. A removes the scroll entirely above
the floor. **That is a far smaller gap than the first framing implied**, and it weakens
the case for the split rather than strengthening it.

**A second defect in that draft, found by re-measuring.** The first version's two wide
`A` frames carried only three categories against `B`'s five, so the sheet's own claim of
identical content was false. Corrected: all six frames now carry the same eight cards,
and the figures above are from after that fix.

The remaining question is not measurable: whether seeing envelopes and categories
*together* is what Budget is for. If the side-by-side view is the point of the screen, the
exception earns its keep; if Budget is a list you scroll like any other, two cards of
scrolling do not justify a third mechanism.

---

## 17. Sci-Fin itself, measured with the same instrument (2026-09-24)

> **Three conclusions in this section are withdrawn -- see §18.1 (2026-09-24).** Rendering the
> composables directly showed that four of the five destinations were taking §4.8 rule 2's
> two-pane branch, not stretching, so the comparison table in §17.4, the bar-aspect table under
> it, and the `client / 2 + 49` attribution in §17.3 are all wrong. The client-rect arithmetic,
> the titlebar finding and the fixed-height rows stand. The section is kept as written because
> what it got wrong is the point: **a boundary tells you where something changed, never what.**

§15 measured four installed apps. §16 scored their policies. Both then fed a series of
hand-built mockups -- and the owner stopped that line of work for the right reason:
*"I am unsure whether this could be easier to work on if on the real app, rather than the
mockup. You also added emojis that are not part of the app."*

Both points were correct, and the second is the symptom of the first.

### 17.1 Three mockups, three drifts from the build

Each rebuild introduced fresh inaccuracies that measurement of the mockup could not catch,
because the mockup was being checked against itself:

1. **The segmented control was missing entirely.** `BudgetScreen.kt:75-79` renders a title
   row, then a `Segmented("Envelopes" | "Categories")`, then **one** of the two views. Every
   sheet in §14 and §16 put both on screen at once. That made the two-column proposal look
   like "split an existing two-pane screen" when it is really "**replace the segmented
   control**" -- a §4.3 change, not a §4.8 one, and a far bigger claim than was being made.
2. **Wrong figures.** €1,095/mo for recurring expenses was carried through four sheets.
   `prototype-seed.json` gives six expenses whose monthly equivalent is **€1,364** (car
   insurance 600÷12 = 50, pest control 90÷2 = 45). Also missing: the catch-up card, the
   learn link, the "received so far this month" caption, the recurring disclosure note and
   the entire Goals section.
3. **Emoji instead of icons.** The app draws Lucide glyphs -- `WALLET`, `BANKNOTE`,
   `REPEAT`, `SHOPPING_CART`, `UTENSILS_CROSSED`, `BUS`, `LIGHTBULB`, `PIGGY_BANK`. The
   mockup substituted emoji, which are not in the app at all.

**The mockups are retired.** What follows measures the running application.

### 17.2 Method

`./gradlew --offline :desktopApp:run`, then the **same two-axis sweep** used on the four
installed apps in §15, targeting the window by title rather than by executable path
(Sci-Fin runs inside a JVM, so its process path is `java.exe`). Width 1920 → 660 → 1920,
height 1000 → 480 → 1000, down and back up, seventeen and fifteen steps. Same boundary
detection, same privacy posture: geometry printed, captures unlinked, nothing read from
the images. The app was closed afterwards and its launch log confirms it recognised a
development launcher and installed no shortcut or scheduler entries.

The app has real data (the owner's own profile), so this is the Budget screen as it
actually renders, not an empty first-run.

### 17.3 What the real app does

**The client area confirms §14.5.1 from a third independent route.** Every step reported
`client = outer − 16` horizontally and `outer − 39` vertically, so a 1100 × 760 window
gives **1084 × 721**. The −39 also places Sci-Fin with **Todoist** and against Notion,
Obsidian and VS Code: it uses the **native** titlebar rather than drawing its own, and
therefore pays the 31px that the other three reclaim.

**Only one vertical boundary moves, and it moves at exactly half the window's rate.**

| client | boundary | client ÷ 2 | offset |
|---|---|---|---|
| 1904 | 1001 | 952.0 | **49.0** |
| 1584 | 841 | 792.0 | **49.0** |
| 1384 | 741 | 692.0 | **49.0** |
| 1184 | 641 | 592.0 | **49.0** |
| 1068 | 583 | 534.0 | **49.0** |
| 984 | 541 | 492.0 | **49.0** |
| 844 | 471 | 422.0 | **49.0** |
| 744 | 421 | 372.0 | **49.0** |
| 644 | 371 | 322.0 | **49.0** |

Nine widths, identical on the down leg and the up leg, offset constant to a tenth of a
pixel. `boundary = client ÷ 2 + 49` is the **midpoint of the content area** -- which is the
divider of the full-width segmented control, the one place the selected and unselected
halves differ in background. Budget today is a single full-width pane, and its content
**stretches with the window**.

**Nothing moves on the height axis.** Rows `35, 131, 197, 318, 414` are identical at every
height from client 441 to 961 -- a fixed-height stack that the window reveals more or less
of, exactly as Notion and VS Code behave.

### 17.4 The comparison, now on equal footing

| App | Chrome | Content behaviour |
|---|---|---|
| Notion | fixed 267 | holds a measure; surplus width left empty |
| Obsidian | fixed 43 + 344 | divides the remainder exactly in half |
| VS Code | fixed 4 + 53 | centres a 946 maximum measure |
| Todoist | fixed 280 / 66 | collapses the nav to protect ~484 of content |
| **Sci-Fin** | fixed rail | **stretches to fill, without limit** |

**Sci-Fin is the only one of the five that stretches its content.** The other four each
found a different way not to, and §15.3 recorded that as unanimous before the app itself
had been measured against it.

**What that costs, as a ratio.** The §2.1 bar is `fillMaxWidth()` at a fixed `height(5.dp)`
(`Progress.kt:51`), so the stretch lands directly on its aspect:

| client | content | bar width | aspect |
|---|---|---|---|
| phone ~360 | ~332 | 308 | **62:1** -- the shape it was designed as |
| 644 | 555 | 503 | 101:1 |
| **1084 -- the default window** | 995 | 943 | **189:1** |
| 1584 | 1495 | 1443 | 289:1 |
| 1904 -- maximised | 1815 | 1763 | **353:1** |

The 194:1 figure in §16 was presented as what a stretched column *would* do. It is not
hypothetical: at its own default window the shipped bar is **189:1**, and maximised it is
**353:1**, five and a half times the shape it was drawn as. The distortion the measure cap
was proposed to prevent is the present state of the application.

---

## 18. What the column rule settled, and three claims in §17 withdrawn (2026-09-24)

### 18.1 §17 measured the window and inferred the composables. The inference was wrong.

§17 drove the running application through §15's resize sweep and read boundaries off the
pixels. Rendering the composables directly, later the same day, showed that three of its
conclusions do not survive. They are withdrawn here rather than quietly dropped.

**Withdrawn 1 — "Sci-Fin is the only one of the five that stretches its content."** It does
not, on four of its five destinations. Budget, Activity, Learn and Settings all took §4.8
rule 2's two-pane branch: a **fixed 368dp list** beside a `weight(1f)` pane that a tab root
leaves holding the words "Nothing selected." Those screens were not stretching, they were
368 wide with **627dp empty at a 1084 client and 1447dp empty at 1904**. Only Reports, which
§4.8 exempts from rule 2, stretched. The last row of §17.4's comparison table is wrong, and
so is the sentence under it.

**Withdrawn 2 — the aspect table in §17.4.** It derived bar width from the full content
width (995 at the default window, 1815 maximised) and reported **189:1** and **353:1**. The
bar was inside the 368dp pane, so its real aspect was roughly **63:1** and did not change
with the window at all. The table measured a layout the app was not using.

**Withdrawn 3 — the `boundary = client / 2 + 49` attribution.** §17.3 read that boundary as
the divider of Budget's full-width segmented control. That was an inference from screenshots
which, by the walk's own privacy posture, were never examined. The same number is equally
consistent with the two-pane split, and nothing measured distinguishes them. The *arithmetic*
stands — nine widths, both legs, constant to a tenth of a pixel — only the attribution goes.

**What survives §17 unchanged:** the client-rect arithmetic (`outer − 16` horizontally,
`outer − 39` vertically, so 1100 × 760 gives 1084 × 721), which agrees with §14.5.1 from a
third independent route; the native-titlebar finding that groups Sci-Fin with Todoist against
the other three; the fixed-height row set on the vertical axis; and the general point that
§2.1's bar aspect is unbounded whenever its container is. The lesson is narrower than "the
measurement was bad": **a boundary tells you where something changed, never what it was.**

### 18.2 §16's one unmeasurable question is now answered

§16 closed by naming the question its own instrument could not reach:

> The remaining question is not measurable: whether seeing envelopes and categories
> *together* is what Budget is for.

It was answered by the owner on 2026-09-24, and not on Budget's terms but on the platform's:

> The desktop app is supposed to be a way to log data faster, it's optional and should offer
> a broader view of information than the phone app.

That premise was absent from §4.8 entirely, which is why the section could only argue
defensively about density — with nothing to weigh breadth against, every gain in breadth
scored as pure register risk. With it stated, the side-by-side view earns its keep. The
decision, its four-point answer to §4.8's register argument, and the line that bounds it
(**another view of the same destination, never another destination**) are recorded in
§4.8 and §4.3 of the specification.

### 18.3 The survey had already said so, and it was not cited

§6's *Standing* paragraph, written 2026-09-23 from the nine-app survey, reached the same
place from evidence rather than from the owner's intent:

> the register argument in §4.8 is *weakened* by the survey, not strengthened. KMyMoney shows
> width and verdict-colour are independent, so the reopened section does not need to choose a
> narrow layout to stay calm; it needs to keep §2.1, which it already mandates.

That is the strongest available support for the 2026-09-24 decision, it predates the decision
by a day, and it was **not brought** when §4.8's register argument was raised as an objection.
Recorded here because a register that holds the answer and does not surface it is a register
that is not being read.

### 18.4 What shipped

| | before | after |
|---|---|---|
| Budget @ 1084 | 368 list + 627 empty | two columns of one measure, no segmented control |
| Budget @ 1904 | 368 list + 1447 empty | two columns, centred, 446 margin each side |
| Budget @ 360 | unchanged | unchanged — the rule is a maximum |
| Reports' segmented control @ 1904 | 1815 wide | 458, centred |
| Recurring summary at two columns | one collapsed row | unwrapped to its six occurrences |

`Page` takes a `maxColumns` opt-in (default 1, so sixteen screens are arithmetically
unchanged), `columnsIn()` states the rule as arithmetic, and `LocalPageColumns` publishes the
count to content that needs it. **236 tests, 0 failures**, with `ColumnRuleTest` pinning the
boundaries at 927/928 and 1397/1398 — the first test in the suite that would catch a layout
regression, since none of the render tests compare pixels.

**Three consequences were left open here. One is now closed.**

- **§4.8's Reports exception -- closed the same day.** It claimed the charts are "more legible
  wide than tall". They are six fixed points: `ageOfMoneyTrend`, `savingsVsOwedTrend` and
  `incomeVsExpenses` each take `months: Int = 6` and every call site took the default, so width
  bought gaps rather than resolution. The width clause is withdrawn, Reports follows the column
  rule, and it gained a supporting column instead -- scored across 1771 weightings, not argued.
  §4.6 then made the window the person's choice at 3, 6, 12 or 24 months, which is what makes the
  withdrawn clause's premise false rather than merely unsupported.
- **§2.1's bar is ~86:1 inside a column** at the default text size, and the hatch past the tick is
  a few pixels wide, which §2.1 calls load-bearing information. Still open.
- **The measure grows with the text ramp**, so at XL the default window holds one column rather
  than two. Still open.

**The canonical list of what is open lives in §13.1 of the specification, not here.** As of
2026-09-24 it holds nine items, the two above among them, each named with what would settle it;
§13.3 holds the four that are decided but unbuilt. This register records how the desktop
questions were measured and what the measurements overturned -- it is not a backlog, and a second
copy of one would drift from the first within a week.
