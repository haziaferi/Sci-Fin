## Recommendation

**Read this first.** The pixel scorers in `tools/` cannot run today: the 2026-10-11 tidy recycled
`native/*/build`, and the render tests that fed them (Scratch-, Placement-, ScratchFix-, ScratchCalib-,
Structure2RenderTest) are gone from source. The one surviving writer is `MeasureRenderTest` (`SCIFIN_MEASURE=1`):
density 1, 192 frames over phone 320–480 and desktop 932–1520, text scale 1.0/1.3/1.5, comfortable/compact, four
views, one theme, probes in `measure.jsonl`. Everything below ran on that.

### Ported and run — `tools/borrowed_axes.py` (results: `borrowed-axes-2026-10-14.tsv`)

| Axis | From | What it said about Sci-Fin today |
|---|---|---|
| **clutter** (edge share) | Factotum `screen_score.py:152` | Phone 0.061–0.077 by view, mean of text scales and densities. That is the top of Factotum's reference-app range (0.011 Loop … 0.079 Anytype); Budget (0.071) and Buffer (0.077) are the busiest. Desktop 0.029–0.044. Spending is the calmest view on both platforms. |
| **Add mass share** | Factotum `sheet_weight.py:45` | Phone FAB holds 5–7% of the page's visual mass, falling as the window widens. Desktop "New category" row 39–46% at 932–1264, but that box spans 457 dp, so the share is mostly the row's width: it needs the importance-order check (`emphasis_coherence`) before it means anything. |
| **Add reach** (Fitts, phone) | Factotum `capture_opt.py:82` | FAB 1.95–2.45 bits (grows with width, as the FAB moves right of the thumb); the inline "New category" row 0.99–1.74. |
| **Add spread / consistency** | PaperKnife `score_layout.py:181`, `measure.py:1111` | Phone FAB steady: spread 3.5–9.9 dp, consistency 90–95. **Desktop Add moves 96–232 dp across text scale × density at one window width** (consistency 0 for "New category"; "New goal" 28 dp at 1084–1264, 118 dp at 1520). End-of-list rows move with the content above them by construction, so the defect flag is expected for them; it matters only because the desktop has no fixed Add. This is the open desktop-Add question, now measured. |
| **small taps** | Factotum `screen_score.py:173` | Desktop `el:goal` (e.g. 54×24), `el:income`, `el:recurring` under 40 dp tall — to check: `Ledger.kt:55` probes whatever it decorates, which may be a label, not the hit box. `round:*` excluded: those probes are the drawn circle, the hit box is the larger `reach` (`Controls.kt:101`, `RoundButtonTest`). |
| **overflow** | PaperKnife `measure.py:1176` | 0 rail labels overflow at any width or text scale. |

Validation: `--selftest` pins each formula to a hand-computed case. Two facts it surfaced about Factotum's clutter
measure, both kept so the numbers stay on Factotum's scale: PIL leaves `FIND_EDGES`' outer pixel ring unfiltered,
so a light page's frame adds 2(W+H)/(W·H) (0.0078 at 360×900 — four times the 0.002 tie); and only the bright side
of an edge responds, so each edge counts once. `clutter_inner` (in the TSV) drops the ring for comparing widths.

### Port next — need a probe or a render the test does not yet make

1. **Concepts Sci-Fin has never measured:** text overflow at 1.3×/2.0× for **money figures** (chronicle
   `text_overflow_robustness`, PaperKnife `clipping_overflow_defects`) — needs an overflow flag on amount probes;
   **mis-tap safety** (Factotum `groggy_tap_risk`, σ 19 dp) for destructive actions next to Save; **copy quality**
   and **judgement marks** for over-budget wording (Factotum `copy_tone`, `judgment_marks`; PA `copy_register_guard`);
   **settings order** (chronicle `frequency_first`, `setup_before_use`, `destructive_last`) for the settings hub.
2. **Colour, cheapest of all:** `dk audit` (design-optimization `engine/designkit/audit.py`) already parses Kotlin
   `Color(0x…)` — declared-pair contrast, CVD collapse of categories, OLED floor. Chronicle's lesson applies: build
   the pair table from where colours are *used* (TextButtons draw `primary` as text → 4.5:1, not 3:1).
3. **Factotum's control gate** — every scored part gets a deliberately bad option; if it is not beaten, the run
   says the measures do not discriminate. Sci-Fin's scorers have no such guard; it is the most valuable *method*
   in the harvest.
4. **Re-establish renders for the old axes:** a render test at 2.625 / 1080×2340 with per-frame probe JSON (or
   make density a parameter of the old scorers) would bring back function/structure/placement scoring.

### Decided against

- `register_fit` from app-register-layout: Sci-Fin's own audit found it scored every candidate 0.858; the skill's
  SKILL.md and `register.py:91` also disagree on its weights (0.2875/0.2375/… vs 0.28/0.24/…).
- craftkit `imgstat` thresholds: calibrated for illustrations; mnemo withdrew a finding (T8) that misread one as
  text legibility.
