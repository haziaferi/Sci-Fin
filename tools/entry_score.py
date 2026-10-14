"""The floating +'s entry, three ways (2026-10-09; owner: "could any of these issues be solved with a natural
language input field?", scored against Factotum's capture line T1.3 and its When sentence T1.5 + T1.6).

  S  the sheet: Direction segmented, Amount, Payee, Date, Cancel/Confirm (today's build)
  L  Factotum's capture line: one line, what it understood highlighted in the text, a readout whose direction is a
     tappable word, Enter or Confirm; asks when a line holds two amounts (EntryParseTest: 22 cases, broken rules fail)
  W  the When-style sentence: "Money out EUR [amount] today at [payee]", each accent word an option

Measured on the renders (native/shared/build/placement/entry*): the sheet's span from title to buttons, in dp.
Counted from each flow: taps and keystrokes on a phone, and the keystroke-level model's time on a desktop (Card,
Moran & Newell: K 0.28, P 1.1, click 0.2, H 0.4, M 1.35), for "EUR 12.50 coffee" today and for the same yesterday.

    python tools/entry_score.py
"""
import sys

K, P, B, H, M = 0.28, 1.1, 0.2, 0.4, 1.35
def klm(p, b, h, k): return M + p * P + b * B + h * H + k * K

ROWS = [
    # name, span phone, controls, hidden grammar, phone taps/keys today, yesterday, desktop KLM today, yesterday
    dict(option="S the sheet", span=488, controls=8, hidden=0,
         ph_today=(4, 11), ph_yday=(6, 11), dk_today=klm(3, 3, 2, 12), dk_yday=klm(5, 5, 2, 12)),
    dict(option="L capture line (T1.3)", span=215, controls=4, hidden=1,
         ph_today=(3, 12), ph_yday=(3, 22), dk_today=klm(2, 2, 1, 13), dk_yday=klm(2, 2, 1, 23)),
    dict(option="W sentence (T1.5 + T1.6)", span=206, controls=6, hidden=0,
         ph_today=(4, 11), ph_yday=(5, 11), dk_today=klm(3, 3, 3, 12), dk_yday=klm(4, 4, 3, 12)),
]

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    keys = [("span", -1), ("controls", -1), ("hidden", -1), ("dk_today", -1), ("dk_yday", -1), ("ph_taps_t", -1), ("ph_taps_y", -1)]
    for r in ROWS: r["ph_taps_t"], r["ph_taps_y"] = r["ph_today"][0], r["ph_yday"][0]
    for a in ROWS:
        a["dominated_by"] = [b["option"].split()[0] for b in ROWS if b is not a and all(s * b[k] >= s * a[k] for k, s in keys) and any(s * b[k] > s * a[k] for k, s in keys)]
    print(f"{'option':28} {'sheet dp':>8} {'controls':>8} {'hidden':>6} {'phone taps+keys today':>21} {'yesterday':>9} {'desktop s today':>15} {'yesterday':>9}  front")
    for r in ROWS:
        print(f"{r['option']:28} {r['span']:8} {r['controls']:8} {r['hidden']:6} {r['ph_today'][0]:>12} + {r['ph_today'][1]:<6} {r['ph_yday'][0]:>3} + {r['ph_yday'][1]:<3} {r['dk_today']:15.2f} {r['dk_yday']:9.2f}  {'yes' if not r['dominated_by'] else 'dominated by ' + ', '.join(r['dominated_by'])}")
    print("\nWith the field focused on open (any style), subtract one point-and-click: 1.3 s on a desktop, one tap on a phone.")

if __name__ == "__main__":
    main()
