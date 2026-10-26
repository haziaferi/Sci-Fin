
# Sci-Fin — Research & Audit Findings

*A science-backed audit of the current app (BudgetFlow) against research on financial psychology, habit formation, and neurodivergent-friendly design.*

**Status: findings only.** Nothing has been changed in the app yet. This document is meant to be the shared reference point for deciding, together, what to actually build next.

---

## 1. What the app actually does today

The current app ("BudgetFlow") is a fully offline, zero-based ("envelope") budgeting app — architecturally a lean clone of YNAB. It's a single-page app (`index.html`, ~2,050 lines) wrapped in Capacitor for Android. Before getting to the science, here's an honest inventory of what exists.

### 1.1 Core features present today

| Area | What's there |
|---|---|
| **Budget view** | "Ready to Assign" zero-based budgeting: category groups → categories, each with a monthly budgeted amount, activity, and available balance. Per-category "goal" is a single monthly spending target with a progress bar. |
| **Accounts** | Multiple budget + off-budget ("tracking") accounts, opening balances, move-money between categories. |
| **Transactions** | Full add/edit/delete, payee & category autocomplete, split-free single-category entries, reconciliation against a statement balance, CSV bank-import with column mapping. |
| **Reports** | Four tabs: Spending by Category, Income vs. Expenses (6mo), Net Worth, Age of Money. All chart/table-based. |
| **Settings** | Light/dark theme toggle, JSON export/import (full backup), CSV import, reset-all, PIN or biometric app lock. |
| **Notifications** | Local-notification plumbing exists but is currently wired only as an over-budget alert stub (`maybeNotifyOverBudget`) — i.e., the only planned notification is a warning, not encouragement. |
| **Widget** | Home-screen balance widget, fully wired. |
| **Security** | SHA-256 PIN gate + biometric fallback (not encryption of the data file itself). |

### 1.2 What's genuinely strong and worth preserving

- **The envelope/category structure itself is well-supported by the literature** (see §3.3) — this is not a wrong foundation, it's a good one that's currently under-used.
- **Fully offline, local-first data model** — no account required, no third-party data sharing. This is a real asset for a "science-based, trustworthy" brand and also *reduces* one source of financial anxiety (data-privacy worry) that many finance apps introduce.
- **Reconciliation, CSV import, Age-of-Money and Net-Worth reporting** are genuinely useful, non-trivial features that many competing apps charge for.
- Reasonable baseline accessibility hygiene already exists: `aria-label`s on icon-only buttons, `prefers-color-scheme`-aware theming, semantic form labels.

### 1.3 What's structurally missing (relative to the brief)

- **No "Learn"/insights section exists in the code at all**, despite being referenced as an idea in the project README. There is currently zero in-app education, explanation, or "why does this matter" content anywhere.
- **No personalized calculator or savings-goal planner.** The only "goal" concept in the data model is a flat monthly spending cap per category — there's no support for a multi-month objective ("save $3,000 for a trip by August"), no target date, no suggested per-paycheck contribution, no visualization of a *savings* goal (as opposed to a spending limit).
- **No onboarding flow.** A first-time user lands directly on an empty envelope-budgeting grid with YNAB-style jargon ("Ready to Assign," "Age of Money") and no explanation.
- **Only one axis of customization exists: light/dark.** Font, type size, density, accent color, motion, and information-density are all hard-coded (fixed 14px base size, single font pairing, fixed color tokens throughout the CSS).
- **No positive reinforcement anywhere in the product.** The only behavior-triggered messaging designed so far is a warning (over-budget). There's no acknowledgment of hitting a savings goal, building a streak, or improving month-over-month.
- **No gamification, streak, or progress-celebration mechanics** of any kind.
- **No accessibility/sensory settings** beyond the theme toggle — no reduced-motion option, no adjustable text size, no dyslexia-friendlier font option, no "focus mode" / reduced-information view.
- The working data model's only "goal" field is literally named `goal` but means *spending ceiling*, which is a different psychological object than a *savings target* — worth flagging because it will matter for how a "goal calculator" gets designed.

---

## 2. How people actually relate to money (the psychology)

### 2.1 Money isn't experienced as fungible

Behavioral economist Richard Thaler's **mental accounting** research shows people don't treat all money the same — they mentally sort it into categories by source or intended use (rent money, fun money, refund money), even though a dollar is a dollar. <cite index="1-1,3-1">This is well documented, including the specific finding that people treat windfalls like tax refunds as "extra" money to be spent freely rather than as part of overall wealth.</cite> Related to this is the **"pain of paying"** — <cite index="3-1">spending money is emotionally uncomfortable because it registers as a loss, and that discomfort is measurably reduced by payment methods (like cards) that separate the moment of spending from the moment money actually leaves an account.</cite>

*Why this matters for Sci-Fin:* mental accounting is usually framed as a *bias* to correct — but it's also the exact mechanism that makes envelope budgeting work in the first place (see §3.3). The category structure already in the app is aligned with this research; the question is whether the interface makes each "envelope" feel emotionally real and separate, or just like more rows in a spreadsheet.

### 2.2 Scarcity narrows the mind before it depletes the wallet

<cite index="78-1">Mullainathan and Shafir's "bandwidth tax" research shows that scarcity — of money or time — occupies cognitive resources with the immediate problem, leaving less mental bandwidth available for everything else, including planning and self-control.</cite> <cite index="82-1">This can produce "tunneling" (fixating on the shortfall and neglecting other information) and a shift toward present-oriented rather than future-oriented thinking.</cite> Critically, this isn't a personality trait — <cite index="83-1">it's a situational cognitive state that affects anyone under financial stress, and it's the stress itself, not a character flaw, that produces worse financial decisions.</cite>

*Why this matters:* an app that adds cognitive load (dense tables, jargon, many decisions per session) is hardest to use exactly when someone is most financially stressed and most needs it to work.

*Narrowed 2026-10-25 (see §2.7): the attention-capture part of scarcity holds up reasonably well in replication; the broader "bandwidth tax" — that scarcity depletes cognitive capacity in general — does not, and no Learn topic relies on it.*

### 2.3 People look away from bad financial news

The **"ostrich effect"** describes the well-replicated tendency to avoid checking financial information when it might be bad news. <cite index="70-1">People tend to underestimate the negative effects of ignoring risk, relying instead on hopeful assumptions that things will work out.</cite> Separately, research on shame specifically (as distinct from guilt) finds <cite index="72-1">shame is tightly linked to avoidance — people who feel ashamed about financial difficulty tend to withdraw and disengage from financial information altogether, which then worsens their situation further.</cite> This creates a vicious cycle: falling behind → shame → avoidance → falling further behind.

*Why this matters:* the one behavior-triggered message currently planned in the code (`maybeNotifyOverBudget`) is a warning. Warning-only feedback loops are exactly the pattern this research says backfires for anyone already stressed about money.

### 2.4 The future self feels like a stranger

<cite index="10-1,12-1">Hershfield's "future self-continuity" research (including neuroimaging) finds people who feel less psychologically connected to their future selves save less — the brain processes "future me" more like a stranger than an extension of the current self.</cite> <cite index="16-1">This sense of connection to one's future self is trait-like but also malleable, which is why it's been a productive target for intervention.</cite> Interventions that strengthen this connection (visualization, concreteness, vivid framing of what the money is *for*) have shown measurable effects on saving behavior in controlled studies.

### 2.5 Asking for what you've earned

*Added 2026-10-23 for the Learn topic on time and asking (spec §10), from a fresh check of the primary sources rather than the original audit.*

Babcock & Laschever's *Women Don't Ask* (2003) made the case that women initiate pay negotiations far less often than men — in their Carnegie Mellon cohort, 57% of men negotiated a starting salary against 7% of women. Later evidence complicates the "don't ask" half. In Australian workplace data, Artz, Goodall & Oswald (2018) found women ask for raises as often as men but succeed less often (15% against 20%); Pew (2023) found a small gap in asking (28% against 32%) and, among those who asked, women more often got only the original offer. Bowles, Babcock & Lai (2007) showed why caution can be rational: evaluators penalised women more than men for asking for higher pay.

Avoiding the conversation is common across genders. Hunsaker, Zhang & Lee (2025), across five studies of nearly 6,000 US adults deciding whether to negotiate purchases, found 77% have passed up a chance to negotiate; people wanted savings of 21–36% of the price before negotiating felt worthwhile, and about half would pay a premium to avoid it. Their prompt comparing the saving with the person's own hourly wage did not change willingness to negotiate. Asked why they didn't negotiate pay, people most often say they were satisfied with the offer (39%) or uncomfortable asking (38%; women 42%, men 33%) (Pew, 2023).

The surrounding circumstances are documented unevenly. The wage gap is solid (EU 12.0% in 2023; in the US women earn 83% of men's median weekly earnings, 2024), and so is unpaid care (ILO: women do about three-quarters of it). The "pink tax" is contested (NYC DCA 2015 against Moshary, Tuchman & Bhatia 2023), and a disproportionate share of household *financial* labour is not established.

*Why this matters:* the topic can honestly call caution rational, but on the grounds of backlash and lower returns rather than a claim that women ask less. The calculator should show the number without presenting it as a nudge — the 2025 hourly-wage prompt did not move anyone. The wage gap and unpaid care are supportable; the pink tax and financial labour, as stated, are not.

### 2.6 What the monthly payment hides

*Added 2026-10-23 for the Learn topic on borrowing (spec §9.1, §9.2), from a fresh check of the primary sources.*

Stango & Zinman (2009) describe **exponential growth bias** — the tendency to treat compounding as if it were linear — which leads people to underestimate the interest rate implied by a loan's payments; in US survey data, more-biased households borrow more and save less (correlational). Argyle, Nadauld & Palmer (2020), studying about 500,000 US used-car loans through discontinuities in lenders' terms, found demand more sensitive to the loan's length than to its rate, borrowers bunching at round monthly payments, and many spending lower payments on larger loans — for borrowers who were not short of cash as well as for those who were.

For cards, Stewart (2009) found among 248 UK cardholders that the minimum correlated with partial repayments, and in a hypothetical-statement experiment (413 participants) that leaving the minimum off raised mean partial repayments by 70%, with the share paying in full unchanged. Keys & Wang (2019), using a quarter of the US general-purpose card market (2008–2013), found 29% of accounts regularly pay at or near the minimum, and that at least 22% of near-minimum payers (9% of all accounts) respond to changes in the minimum formula in a way consistent with anchoring rather than a tight budget alone; the CARD Act's three-year repayment disclosure was taken up by fewer than 1% of accounts.

*Why this matters:* the topic can say the monthly payment hides the cost and that a printed minimum pulls repayments down. It cannot claim that a payoff table fixes this — Stewart proposed one without testing it, and the CARD Act disclosure barely moved behaviour. Some near-minimum payers are genuinely constrained, which supports keeping existing balances neutral. The saving-first lean is the app's stance, reasoned from these findings, not a finding itself.

### 2.7 What money on hand buys

*Added 2026-10-25 for the Learn topic linked from Reports' Age of Money view (spec §5.1), from a fresh check of the primary sources.*

Ruberton, Gladstone & Lyubomirsky (2016) paired the account records of 585 UK bank customers with their Satisfaction With Life Scale responses. Higher balances in current and savings accounts predicted higher perceived financial well-being — less lost sleep over money, more confidence about it — which in turn predicted higher life satisfaction, controlling for income, investments, spending, indebtedness and demographics. The data are correlational, and the authors say so; their "minimal buffer" reading rests partly on a log scale.

Shah, Mullainathan & Shafir (2012) found in lab games that scarcity focuses attention on the pressing problem and leads to over-borrowing; their own high-powered replication reproduced the over-borrowing, though evidence for attentional neglect is weaker (de Bruijn & Antonides, 2022). The broader bandwidth claim of Mani et al. (2013) is contested: Wicherts & Zand Scholten (2013) could not reproduce it with income analysed continuously; its fluid-intelligence effect did not hold in replications (Dalton et al. 2019; Fehr et al. 2019, via de Bruijn & Antonides); low-income households tested before payday showed no cognitive deficit (Carvalho, Meier & Wang, 2016); and an audit of 20 scarcity-priming studies found 4 significant replications (O'Donnell et al., 2021).

*Why this matters:* the topic can say cash on hand goes with feeling secure, and that scarcity pulls attention towards itself. It cannot say money worries impair thinking in general, or that a higher Age of Money improves well-being. Calling the figure a cushion rather than a score is the app's own reading of these findings, not a finding itself.

### 2.8 Uneven months

*Added 2026-10-25 for the Learn topic linked from Reports' Income vs Expenses view (spec §5.4), from a fresh check of the primary sources.*

In the US Financial Diaries (Hannagan & Morduch, 2015; 244 low- and moderate-income households), income was more than 25% above or below a household's own average in about five months of the year; 61% of spending spikes fell in months without an income spike, and 33% in months when income was below average. In JPMorgan Chase Institute data (2015; 100,000 account holders), 41% saw month-to-month income swings over 30%, consumption was more volatile still, the highest earners were as volatile as the lowest, and income and consumption changes "did not move in tandem".

Where pay is regular, spending rises when it arrives: Stephens (2003) for Social Security recipients in Consumer Expenditure Survey diaries, mainly those relying on it, with small effects; Shapiro (2005) for food-stamp recipients, whose calorie intake fell 10–15% over the benefit month. Gelman et al. (2014), using personal-finance app data, found most of the response comes from regular bills falling due when pay arrives, with the rest concentrated among people with less liquidity.

*Why this matters:* the topic can say uneven months are common across incomes, and that spending following pay is partly just bill timing. It should not prescribe smoothing: Stephens judged the losses small, and the residual falls on people with less on hand — a fact about their circumstances, not their choices.

---

## 3. What actually makes a budgeting habit stick

### 3.1 Specific plans beat good intentions — with a caveat

Gollwitzer's **implementation intentions** ("if X happens, I will do Y") reliably help translate intentions into action across many domains. <cite index="18-1">In a financial-behavior study specifically, requiring people to state *how* and *when* they'd save was more effective than a vague commitment to save "in general," and having a concrete plan made people more likely to actually follow through on alternative ways to save when the original plan wasn't available.</cite> The caveat: <cite index="20-1">newer research finds if-then plans can also make behavior *less flexible* — they're best suited to stable, repeatable situations, and can misfire if applied rigidly to situations that change.</cite> This is a nuance worth carrying into any "set a plan" feature — plans should be revisitable, not locked in.

### 3.2 Removing the decision beats relying on willpower

The strongest, most replicated result in this entire literature is about **defaults and automation**. <cite index="90-1">In the original Save More Tomorrow research, employees who pre-committed to automatically increasing their savings rate at future pay raises ended up saving significantly more than those left to decide for themselves each time — largely because the same inertia that normally prevents people from saving was redirected to work in their favor.</cite> <cite index="86-1">Auto-enrollment in retirement plans alone has been shown to send participation rates soaring purely by changing what happens when someone does nothing.</cite> This is arguably the single most actionable finding for any personal-finance product: **friction and defaults matter more than dashboards and willpower.**

### 3.3 Naming and separating money for a goal works — the envelope model is scientifically sound

This directly validates the app's existing architecture. <cite index="94-1">Soman and Cheema's field research found that having people label savings for a specific purpose ("earmarking") and physically/visually segregate that money increased savings substantially, with the effect of visually partitioning money into separate labeled "envelopes" being even larger than labeling alone.</cite> <cite index="100-1">The presence of a visual reminder of the goal specifically increased the savings rate on top of the labeling effect.</cite>

The nuance worth knowing: <cite index="98-1,99-1">follow-up research on earmarking found it can have an unintended side effect — people sometimes hold onto money labeled for a specific "responsible" purpose even during a genuine emergency, preferring to take on high-interest debt rather than "break" the labeled savings, out of guilt.</cite> A goal/envelope system that leans entirely on guilt as its enforcement mechanism can therefore create its own bad outcomes.

### 3.4 Gamification helps — but only when it's attached to something real

<cite index="27-1">Industry analysis of gamified fintech features suggests the strongest results come specifically from tying rewards to behaviors that genuinely benefit the user (saving, on-time payments, learning), not gamifying for its own sake.</cite> There's real effect-size evidence behind this when done well, but also a real failure mode: <cite index="30-1">at least one large longitudinal analysis found gamification raised users' *confidence* in their financial ability faster than it raised actual financial literacy — i.e., gamification can create a feeling of competence that outpaces the underlying skill, which is its own risk.</cite> The design implication is that streaks/badges/progress bars are a legitimate tool, but only if they're always attached to something meaningfully true about the user's finances, not decorative point-scoring.

### 3.5 Autonomy-driven motivation beats pressure-driven motivation

Applying **Self-Determination Theory** directly to personal finance, <cite index="115-1">a large two-study survey found that people whose motivation to manage money was autonomous (chosen, valued) — rather than externally pressured — showed better saving/investing behavior, higher financial self-efficacy, and higher financial well-being; motivation driven by external pressure was associated with *lower* financial well-being, and simply lacking motivation altogether was associated with more overspending.</cite> In plain terms: an app that nags, pressures, or shames tends to correlate with the worst outcomes in this research — not the best.

### 3.6 Why budgeting apps typically die (this is the part most relevant to the current architecture)

A behaviorally-focused breakdown of budgeting-app failure identifies a specific, repeating cycle: <cite index="103-1">tracking every transaction and adjusting rigid category limits requires ongoing mental energy; when that energy runs low, people skip updates, fall behind, feel ashamed about falling behind, and disengage entirely — turning a tool meant to help into a source of anxiety.</cite> <cite index="103-1">Rigid categories with hard limits are specifically implicated: going even slightly over a category triggers a feeling of failure that people experience as personal rather than as a normal, correctable event.</cite>

This isn't just commentary — it shows up in controlled data too. <cite index="107-1">A field experiment on budgeting engagement found that even a well-designed, low-effort budgeting intervention only modestly increased how often people checked their budget, and — across every condition tested, including the improved ones — engagement declined steadily over time.</cite> Sustained daily/weekly engagement with a manually-maintained budget appears to be a genuinely hard problem, not a UI polish problem.

*This is directly relevant to the current app*, which is a fairly strict envelope system (over-budget categories go visibly red/negative) with no softening mechanism, no positive counter-signal, and a notification system currently scoped only to warn, not to encourage.

### 3.7 The real "north star" isn't budget adherence — it's financial well-being

<cite index="125-1">The CFPB's Financial Well-Being Scale defines financial well-being as the degree to which a person's financial situation and financial capability provide them with security and freedom of choice</cite> — not "did they follow every line of their budget this month." <cite index="123-1">Research applying this framework finds that financial *confidence* in one's ability to reach goals, and having a habit of planning, are themselves positively linked to financial well-being — somewhat independent of raw account balances.</cite> This reframes what a "successful" session in the app should even look like.

---

## 4. Neurodiversity: what the research says

The brief specifically asked this to account for neurodiverse people, so this section stands on its own rather than being folded into general UX notes.

### 4.1 ADHD

<cite index="35-1">ADHD affects financial management primarily through executive-function channels — planning, organization, working memory, and impulse control — all of which are load-bearing for budgeting specifically.</cite> <cite index="38-1">Everyday financial friction (remembering a due date, locating a bill, following through on payment) is often the actual barrier, not a lack of money or lack of care — and shame frequently attaches to this even though the root cause is neurological, not a character flaw.</cite> <cite index="37-1">Automation specifically is highlighted as the highest-leverage intervention, because it removes the need for repeated willpower and decision-making that executive function differences make unreliable; visual tracking and external accountability are also called out as helpful because they externalize what working-memory would otherwise have to hold internally.</cite> <cite index="41-1">Goal-setting itself is flagged as especially difficult for ADHD specifically because it draws on multiple executive-function skills simultaneously — which is directly relevant to how a "goal calculator" gets designed (it should do the multi-step planning *for* the person, not ask them to hold it all in mind).</cite>

### 4.2 Autism

<cite index="42-1">Research specifically on financial planning for autistic adults notes that alongside genuine challenges (executive-function difficulty, anxiety around financial decisions), autistic adults often bring real strengths to money management — attention to detail, pattern recognition, and comfort with following an established system — and that personalized approaches beat one-size-fits-all ones.</cite> <cite index="42-1">Financial education specifically lands better when it's visual (charts, concrete flowcharts) and concrete rather than abstract.</cite> On sensory design specifically, <cite index="43-1">recent accessibility-in-finance analysis points to a converging set of features that help: low-distraction "simplified" modes that reduce sensory/cognitive load, breaking complex actions into single clear steps, visual budgets instead of text-heavy dashboards, and nudges designed to reduce overwhelm rather than to gamify everything.</cite>

### 4.3 General cognitive accessibility (benefits ADHD, autism, and everyone else under stress)

Standard accessibility guidelines (WCAG) were built primarily around visual/motor/auditory impairment and only partially cover cognitive accessibility. <cite index="56-1">A synthesis of neurodivergent-specific UX guidance built on top of WCAG highlights that autistic users in particular benefit from high predictability and can be pushed into cognitive overload by ambiguity or a lack of structure, while ADHD users are more affected by attention regulation, impulsivity, and working-memory lapses — meaning the design goal should shift from "can the task be completed" to "does this conserve the user's mental energy and sense of autonomy."</cite> <cite index="63-1">A step-by-step flow with one task per screen, shorter forms, and clear progress indicators is specifically called out as helping ADHD users — and, notably, helping everyone else too, especially under stress or fatigue.</cite> Concrete, converging recommendations across multiple sources include:

- One task/decision per screen rather than dense multi-column tables (directly relevant — the current Budget view is a 5-column dense table).
- Consistent, predictable interaction patterns (WCAG's "no unexpected changes on input" principle).
- <cite index="60-1">User-adjustable timing rather than fixed time limits, which specifically benefits ADHD and processing-speed differences.</cite>
- <cite index="60-1">User control over motion/auto-playing content, since unprompted movement is a common distraction and sensory-overload trigger.</cite>
- <cite index="59-1">Icons paired with text labels rather than icon-only controls, since ambiguous symbols are a specific problem for literal thinkers.</cite>
- Adjustable text size/contrast and font choice, since dyslexia and visual-processing differences are common and co-occurring in both ADHD and autistic populations.

---

## 5. Gap analysis — mapping the research onto the current app

| Research-backed principle | Present in the app today? | Notes |
|---|---|---|
| Category/envelope structure with real psychological separation of funds (§2.1, §3.3) | **Partially.** Structure exists, but categories are plain table rows — no visual reminder/imagery per goal, which the earmarking research shows adds an independent boost. |
| Automation / smart defaults over manual willpower (§3.2) | **Absent.** Everything (budgeting amounts, category assignment, goal tracking) is fully manual, every month. |
| Concrete, revisitable goal plans (§3.1) | **Absent** for savings goals specifically. The only "goal" concept is a static monthly spend cap. |
| Positive reinforcement / progress acknowledgment (§2.3, §3.4–3.6) | **Absent.** The only planned behavior-triggered message is a warning (over-budget). No positive-progress messaging exists anywhere. |
| Reduced decision fatigue / low-friction logging (§3.6) | **Weak.** Manual transaction entry, manual monthly category budgeting, dense multi-column table as the primary daily view. |
| Autonomy-supportive (vs. pressuring) tone (§3.5) | **Neutral-to-punitive.** No explicit shaming copy exists, but the only feedback mechanism designed so far is a warning, and red/negative styling is the only "signal" in the budget grid. |
| Financial well-being framing vs. pure adherence tracking (§3.7) | **Absent.** All reporting is adherence/accounting-oriented (spend, net worth, age of money) — none of it answers "how secure do I feel," which is the actual research-backed definition of financial well-being. |
| Education / "why this matters" content (Learn section) | **Absent from the codebase entirely**, despite being referenced as an aspiration in the README. |
| One-task-per-screen, low information density (§4.3) | **Absent.** Core Budget view is a dense 5-column table; Settings and modals are fairly information-dense forms. |
| User-adjustable text size / font / motion / contrast (§4.3) | **Absent.** Only light/dark theme exists; font, size, spacing, and motion are all fixed. |
| Visual/concrete over abstract, jargon-light framing (§4.2) | **Weak.** YNAB-derived jargon ("Ready to Assign," "Age of Money") is used without explanation anywhere in the UI. |
| Externalized reminders / accountability supports for executive-function load (§4.1) | **Minimal.** Notification plumbing exists but is scoped only to one punitive alert; nothing externalizes bill due-dates, check-ins, or plan follow-through. |
| Personalization / individual profile of goals, strengths, sensory needs (§4.2 personalization point) | **Absent.** No onboarding, no user profile, no way to declare preferences at all. |

---

## 6. Summary of key findings

1. **The underlying architecture (envelope/zero-based budgeting) is scientifically well-supported** — earmarking and partitioning research is some of the most robust evidence in this whole review. The foundation doesn't need to be thrown out; it needs to be made more visual, more forgiving, and extended from "spending caps" to genuine multi-month **savings goals**.
2. **The single biggest gap relative to the research is automation/defaults vs. manual willpower.** Nearly every habit-formation finding here (Save More Tomorrow, auto-enrollment, decision-fatigue research) points the same direction: the app currently asks for full manual engagement every month with no automation layer at all.
3. **There is currently no positive reinforcement loop anywhere in the product** — only a punitive one (over-budget warnings). The shame/avoidance research (§2.3) and the SDT research (§3.5) both suggest this is close to a worst-case design for long-term engagement, even though it wasn't done maliciously — it's simply the one notification that happened to get built first.
4. **There is no education or insights layer at all**, despite it being part of the original vision and despite jargon (Ready to Assign, Age of Money) being used unexplained.
5. **There is no goal-planning calculator** — the data model's only "goal" is a spending ceiling, not a savings target with a timeline.
6. **Customization is limited to light/dark theme.** For a "friendly, customizable" brand promise, and especially for neurodivergent users, this is the largest structural gap: no text size, font, density, motion, or contrast controls exist.
7. **Neurodiversity research converges on a few concrete, low-ambiguity principles** that the current UI doesn't yet reflect: one task per screen instead of dense tables, predictable/consistent patterns, adjustable timing and motion, icon+label pairing, and — specifically for ADHD — automation and externalized reminders as the highest-leverage intervention; specifically for autism — predictability, low-distraction modes, and concrete/visual explanation over abstract jargon.
8. **"Success" in the app is currently defined purely as accounting adherence** (did you stay under budget), whereas the research-backed definition of financial well-being is about a felt sense of security and freedom of choice — which the app doesn't currently measure, surface, or design toward at all.

---

## Sources

- Thaler, R.H. (1999). *Mental Accounting Matters.* Journal of Behavioral Decision Making.
- Prelec, D. & Loewenstein, G. — "pain of paying" research, summarized via St. Louis Fed, *How Mental Accounting Shapes Our Financial Choices* (2026).
- Mullainathan, S. & Shafir, E. (2013). *Scarcity: Why Having Too Little Means So Much.*
- Galai, D. & Sade, O. (2006); Karlsson, Loewenstein & Seppi (2005) — foundational "ostrich effect" studies.
- Gladstone, J.J. et al. (2021). *How shame intensifies financial hardship.* Organizational Behavior and Human Decision Processes.
- Ersner-Hershfield, H., Wimmer, G.E. & Knutson, B. (2009); Hershfield, H.E. et al. (2011) — future self-continuity research.
- Gollwitzer, P.M. — implementation intentions research; applied-to-savings study via academia.edu (2016).
- Thaler, R.H. & Benartzi, S. (2004). *Save More Tomorrow: Using Behavioral Economics to Increase Employee Saving.* Journal of Political Economy.
- Madrian, B.C. & Shea, D.F. (2001) — 401(k) automatic enrollment research.
- Soman, D. & Cheema, A. (2011). *Earmarking and Partitioning: Increasing Saving by Low-Income Households.*
- Sussman, A.B. & O'Brien, R.L. (2016). *Knowing When to Spend: Unintended Financial Consequences of Earmarking to Encourage Savings.* Journal of Marketing Research.
- Beierlein & colleagues (2025); PMC study on Self-Determination Theory applied to personal financial management motivation.
- CFPB (2015, 2017). *Financial Well-Being Scale* and technical report.
- Babcock, L. & Laschever, S. (2003). *Women Don't Ask: Negotiation and the Gender Divide.* Princeton University Press.
- Bowles, H.R., Babcock, L. & Lai, L. (2007). *Social incentives for gender differences in the propensity to initiate negotiations: Sometimes it does hurt to ask.* Organizational Behavior and Human Decision Processes, 103(1), 84–103.
- Artz, B., Goodall, A.H. & Oswald, A.J. (2018). *Do Women Ask?* Industrial Relations, 57(4), 611–636.
- Hunsaker, D.A., Zhang, H. & Lee, A.J. (2025). *Beyond Propensity: Thresholds, Costs, and Interventions in Negotiation Avoidance.* Negotiation and Conflict Management Research, 18(4), 37–56. https://ncmr.lps.library.cmu.edu/article/id/911/
- Pew Research Center (2023). *When negotiating starting salaries, most U.S. women and men don't ask for higher pay.*
- Eurostat (2025). *Gender pay gap statistics*; U.S. Bureau of Labor Statistics (2025). *Highlights of women's earnings in 2024.*
- ILO (2018). *Care work and care jobs for the future of decent work.*
- Stango, V. & Zinman, J. (2009). *Exponential Growth Bias and Household Finance.* Journal of Finance, 64(6), 2807–2849.
- Argyle, B., Nadauld, T.D. & Palmer, C. (2020). *Monthly Payment Targeting and the Demand for Maturity.* Review of Financial Studies, 33(11), 5416–5462.
- Stewart, N. (2009). *The Cost of Anchoring on Credit-Card Minimum Repayments.* Psychological Science, 20(1), 39–41.
- Keys, B.J. & Wang, J. (2019). *Minimum payments and debt paydown in consumer credit cards.* Journal of Financial Economics, 131(3), 528–548.
- Ruberton, P.M., Gladstone, J. & Lyubomirsky, S. (2016). *How your bank balance buys happiness: The importance of "cash on hand" to life satisfaction.* Emotion, 16(5), 575–580.
- Shah, A.K., Mullainathan, S. & Shafir, E. (2012). *Some consequences of having too little.* Science, 338(6107), 682–685.
- Mani, A., Mullainathan, S., Shafir, E. & Zhao, J. (2013). *Poverty impedes cognitive function.* Science, 341, 976–980; Wicherts, J.M. & Zand Scholten, A. (2013). *Comment on "Poverty impedes cognitive function".* Science, 342, 1169.
- Carvalho, L.S., Meier, S. & Wang, S.W. (2016). *Poverty and economic decision-making: Evidence from changes in financial resources at payday.* American Economic Review, 106(2), 260–284.
- de Bruijn, E.-J. & Antonides, G. (2022). *Poverty and economic decision making: a review of scarcity theory.* Theory and Decision, 92, 5–37.
- O'Donnell, M. et al. (2021). *Empirical audit and review and an assessment of evidentiary value in research on the psychological consequences of scarcity.* PNAS, 118(44), e2103313118.
- Hannagan, A. & Morduch, J. (2015). *Income Gains and Month-to-Month Income Volatility: Household evidence from the US Financial Diaries.* NYU Financial Access Initiative.
- JPMorgan Chase Institute (2015). *Weathering Volatility: Big Data on the Financial Ups and Downs of U.S. Individuals.*
- Stephens, M. (2003). *"3rd of tha Month": Do Social Security recipients smooth consumption between checks?* American Economic Review, 93(1), 406–422.
- Shapiro, J.M. (2005). *Is there a daily discount rate? Evidence from the food stamp nutrition cycle.* Journal of Public Economics, 89(2–3), 303–325.
- Gelman, M., Kariv, S., Shapiro, M.D., Silverman, D. & Tadelis, S. (2014). *Harnessing naturally occurring data to measure the response of spending to income.* Science, 345(6193), 212–215.
- NYC Department of Consumer Affairs (2015). *From Cradle to Cane: The Cost of Being a Female Consumer*; Moshary, S., Tuchman, A. & Bhatia, N. (2023). *Gender-based pricing in consumer packaged goods: a pink tax?* Marketing Science.
- Irrational Labs — field experiment case study on budgeting engagement decay.
- econbrew.com, medium.com/@stefanneculai — behavioral breakdowns of why budgeting apps fail.
- Understood.org, CHADD.org, Relational Psych, JobCannon — ADHD and money management / executive function.
- LinksABA, 11fs.com ("Accessibility in finance") — autism and financial planning/accessible fintech design.
- W3C WAI — *Cognitive Accessibility at W3C* / *Making Content Usable for People with Cognitive and Learning Disabilities* (WCAG supplemental guidance).
- Medium/Bootcamp ("Designing for Neurodivergence"), accessibilitychecker.org, pivotalaccessibility.com — neurodivergent UX design principles.

---

*Next step: whenever you're ready, we can go through this section by section and decide what actually makes it into Sci-Fin — I'd suggest we prioritize rather than try to do everything at once.*
