"""WCAG contrast for every Sci-Fin palette token pair, read straight from Tokens.kt.

Alpha tokens (the dark palettes' bord/bordStrong/txt2/txt3) are composited over the
ground they are measured against before the ratio is taken, so the number is what
the eye actually sees, not the pre-blend colour.
"""
import re, sys, json

import os
TOKENS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "native", "shared", "src", "commonMain", "kotlin", "app", "scifin", "ui", "Tokens.kt")
NAMES = ["bg", "surf", "surf2", "bord", "bordStrong", "txt", "txt2", "txt3", "acc", "accH", "onAcc"]

src = open(TOKENS, encoding="utf-8").read()
pal_re = re.compile(r"val (\w+) = Palette\((.*?)\)\s*$", re.M)
tok_re = re.compile(r'hex\("([0-9A-Fa-f]{6})"\)|rgba\((\d+), (\d+), (\d+), ([0-9.]+)f\)')

def parse(body):
    out = []
    for m in tok_re.finditer(body):
        if m.group(1):
            h = m.group(1)
            out.append((int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 1.0))
        else:
            out.append((int(m.group(2)), int(m.group(3)), int(m.group(4)), float(m.group(5))))
    return dict(zip(NAMES, out))

palettes = {m.group(1): parse(m.group(2)) for m in pal_re.finditer(src)}
assert len(palettes) == 8, palettes.keys()

def over(c, ground):
    r, g, b, a = c
    gr, gg, gb, _ = ground
    return (r * a + gr * (1 - a), g * a + gg * (1 - a), b * a + gb * (1 - a), 1.0)

def lum(c):
    def ch(v):
        v = v / 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b, _ = c
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)

def ratio(fg, bg):
    if fg[3] < 1:
        fg = over(fg, bg)
    l1, l2 = lum(fg), lum(bg)
    hi, lo = max(l1, l2), min(l1, l2)
    return (hi + 0.05) / (lo + 0.05)

# (foreground, ground, threshold, what it carries)
PAIRS = [
    ("txt", "bg", 4.5, "body text on page"),
    ("txt", "surf", 4.5, "body text on card"),
    ("txt", "surf2", 4.5, "body text on inset"),
    ("txt2", "bg", 4.5, "secondary text on page"),
    ("txt2", "surf", 4.5, "secondary text on card"),
    ("txt2", "surf2", 4.5, "secondary text on inset"),
    ("txt3", "bg", 4.5, "tertiary/caption on page"),
    ("txt3", "surf", 4.5, "tertiary/caption on card"),
    ("txt3", "surf2", 4.5, "tertiary/caption on inset"),
    ("onAcc", "acc", 4.5, "button label on accent"),
    ("onAcc", "accH", 4.5, "button label on accent (pressed)"),
    ("acc", "bg", 4.5, "accent as text/link on page"),
    ("acc", "surf", 4.5, "accent as text/link on card"),
    ("acc", "bg", 3.0, "accent as fill/icon on page (non-text)"),
    ("acc", "surf", 3.0, "accent as fill/icon on card (non-text)"),
    ("bordStrong", "bg", 3.0, "control border on page (non-text)"),
    ("bordStrong", "surf", 3.0, "control border on card (non-text)"),
    ("bord", "bg", 3.0, "hairline on page (decorative; 3:1 not required)"),
    ("surf", "bg", 1.0, "card against page (delta only)"),
]

rows, fails = [], []
for pname, p in palettes.items():
    for fg, bg, thr, what in PAIRS:
        r = ratio(p[fg], p[bg])
        ok = r >= thr if thr > 1 else None
        rows.append((pname, fg, bg, r, thr, ok, what))
        if ok is False and "decorative" not in what:
            fails.append((pname, fg, bg, r, thr, what))

if "--json" in sys.argv:
    print(json.dumps([dict(palette=a, fg=b, bg=c, ratio=round(d, 2), threshold=e, pass_=f, carries=g) for a, b, c, d, e, f, g in rows], indent=1))
    sys.exit()

print("| palette | fg | ground | ratio | needs | AA | carries |")
print("|---|---|---|---|---|---|---|")
for a, b, c, d, e, f, g in rows:
    flag = "" if f is None else ("pass" if f else "**FAIL**")
    print(f"| {a} | {b} | {c} | {d:.2f} | {e if e > 1 else '—'} | {flag} | {g} |")
print()
print(f"## Failures ({len(fails)})")
for a, b, c, d, e, g in fails:
    print(f"- {a}: `{b}` on `{c}` = {d:.2f} (needs {e}) — {g}")
