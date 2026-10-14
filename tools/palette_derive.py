"""Derive Sci-Fin's eleven palette tokens per theme and mode from a visual-craft lexicon palette.

Decided with the owner 2026-10-07: every theme sits on the same grounds -- light `#f4f4f2`, dark
`#17181a` -- and the eight lexicon palettes (Serene: Still Water, Chalk; Health: Daylight, Linen;
Academia: Marginalia, Tallow & Tome; Swiss: Teal, Blue) replace the four shipped themes. The theme's
colour shows in tinted cards and insets, and in the accent, which now also draws section titles,
the selected tab and the headline figure -- so the accent is held to the TEXT floor (4.5:1) on
every ground it can sit on, not only the 3:1 a fill needs.

Method (design-optimization's colour maths, used directly -- its palette search models five roles,
not these eleven, and runs a local fallback here): each lexicon colour keeps its OKLCH hue and
chroma; only lightness moves, enumerated in 0.001 steps, and the value nearest the lexicon's own
that clears every floor wins. Chroma is reduced only where the colour leaves sRGB. Contrast and
CIEDE2000 are designkit's own functions.

    python tools/palette_derive.py            # prints the table and gates; exit 1 on any failure
    python tools/palette_derive.py --kotlin   # also prints the Palettes lines for Tokens.kt
"""
import glob, math, os, sys


def designkit_engine():
    """design-optimization's engine folder (the one holding designkit/): DESIGNKIT_ENGINE if set, else the
    newest installed copy among the places Claude keeps plugins on this machine."""
    if os.environ.get("DESIGNKIT_ENGINE"):
        return os.environ["DESIGNKIT_ENGINE"]
    roots = [os.path.join(os.environ.get("APPDATA", ""), "Claude", "local-agent-mode-sessions", "*", "*", "rpm", "*", "engine"),
             os.path.join(os.path.expanduser("~"), ".claude", "plugins", "*", "*", "design-optimization", "*", "engine"),
             os.path.join(os.path.expanduser("~"), ".claude", "plugins", "*", "*", "design-optimization", "engine")]
    found = [d for r in roots for d in glob.glob(r) if os.path.isdir(os.path.join(d, "designkit"))]
    if not found:
        sys.exit("palette_derive: designkit not found -- install the design-optimization plugin or set DESIGNKIT_ENGINE")
    return max(found, key=os.path.getmtime)


ENGINE = designkit_engine()
sys.path.insert(0, ENGINE)
from designkit.colorspace import contrast_ratio as cr, delta_e_hex as de  # noqa: E402

BG = {"light": "#f4f4f2", "dark": "#17181a"}

# name -> {mode: (text, accent)} as the lexicon gives them (accent = the lexicon's `primary`)
LEXICON = {
    "Still Water":   {"light": ("#2B3634", "#41695F"), "dark": ("#DDE5E2", "#8CBFB1")},
    "Chalk":         {"light": ("#35322D", "#75674F"), "dark": ("#E4DFD6", "#C0AE8E")},
    "Daylight":      {"light": ("#22211D", "#2C6050"), "dark": ("#E9E7E1", "#7CBAA2")},
    "Linen":         {"light": ("#26221E", "#8E4826"), "dark": ("#EAE5DF", "#D9906A")},
    "Marginalia":    {"light": ("#1e140d", "#624a32"), "dark": ("#e5dade", "#b79a7d")},
    "Tallow & Tome": {"light": ("#1e1712", "#5e3e2c"), "dark": ("#d8d6d3", "#dfcfb7")},
    "Swiss Teal":    {"light": ("#0f1018", "#0799a9"), "dark": ("#e8f0f0", "#65e2f7")},
    "Swiss Blue":    {"light": ("#131313", "#1D3557"), "dark": ("#f2f2ef", "#729eca")},
}

# floors (the spec's contrast table, with the accent raised to the text floor)
TEXT, TEXT_STRONG, BOUNDARY = 4.5, 7.0, 3.0

# --- sRGB <-> OKLab / OKLCH -------------------------------------------------------------------
def _lin(c): return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
def _gam(c): return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055

def hex_to_oklch(h):
    r, g, b = (_lin(int(h.lstrip("#")[i:i + 2], 16) / 255) for i in (0, 2, 4))
    l = (0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b) ** (1 / 3)
    m = (0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b) ** (1 / 3)
    s = (0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b) ** (1 / 3)
    L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    A = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    B = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    return L, math.hypot(A, B), math.degrees(math.atan2(B, A)) % 360

def oklch_to_rgb(L, C, H):
    A, B = C * math.cos(math.radians(H)), C * math.sin(math.radians(H))
    l = (L + 0.3963377774 * A + 0.2158037573 * B) ** 3
    m = (L - 0.1055613458 * A - 0.0638541728 * B) ** 3
    s = (L - 0.0894841775 * A - 1.2914855480 * B) ** 3
    r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    b = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    return r, g, b

def oklch_to_hex(L, C, H):
    """The nearest in-gamut colour: chroma is reduced until it fits, hue and lightness kept."""
    while True:
        rgb = oklch_to_rgb(L, C, H)
        if all(-1e-4 <= v <= 1 + 1e-4 for v in rgb) or C <= 0:
            return "#" + "".join("%02x" % round(min(1, max(0, _gam(min(1, max(0, v))))) * 255) for v in rgb)
        C = max(0.0, C - 0.002)

def nearest_lightness(seed_hex, ok, C=None, H=None):
    """Enumerate lightness for the seed's hue/chroma; return the passing value nearest the seed."""
    L0, C0, H0 = hex_to_oklch(seed_hex)
    C = C0 if C is None else C; H = H0 if H is None else H
    best = None
    for i in range(0, 1001):
        L = i / 1000
        h = oklch_to_hex(L, C, H)
        if ok(h) and (best is None or abs(L - L0) < abs(best[0] - L0)):
            best = (L, h)
    if best is None:
        raise SystemExit(f"no lightness satisfies the floors for {seed_hex}")
    return best[1]

def derive(name, mode):
    text, accent = LEXICON[name][mode]
    bg = BG[mode]
    dark = mode == "dark"
    _, ca, ha = hex_to_oklch(accent)
    scr = SCREEN.get(name, {})
    tint = scr.get("tint", 1.0)
    if "acc_chroma" in scr:                                    # calm a bright accent before its lightness is searched
        la0, ca0, ha0 = hex_to_oklch(accent); accent = oklch_to_hex(la0, ca0 * scr["acc_chroma"], ha0)
    acc_floor = scr.get("acc_floor", TEXT_STRONG)   # owner 2026-10-07: the accent at AAA in every theme
    # tinted planes in the theme's own hue (the owner's "tinted cards and insets")
    surf = oklch_to_hex(0.250 if dark else 0.980, (0.012 if dark else 0.018) * tint, ha)
    surf2 = oklch_to_hex(0.295 if dark else 0.935, 0.020 * tint, ha)
    grounds = (bg, surf, surf2)
    txt = nearest_lightness(text, lambda h: all(cr(h, g) >= TEXT_STRONG for g in grounds))
    # txt2: the text's hue, as far toward the ground as the text floor allows (a 0.3:1 margin)
    _, ct, ht = hex_to_oklch(txt)
    txt2 = nearest_lightness(oklch_to_hex(0.62 if dark else 0.50, ct, ht), lambda h: all(cr(h, g) >= TEXT + 0.3 for g in grounds))
    txt3 = oklch_to_hex((hex_to_oklch(txt2)[0] + hex_to_oklch(surf)[0]) / 2, ct, ht)        # decorative only (exempt)
    on = bg if dark else "#ffffff"
    acc = nearest_lightness(accent, lambda h: all(cr(h, g) >= acc_floor for g in grounds) and cr(on, h) >= acc_floor)
    la = hex_to_oklch(acc)[0]
    acch = oklch_to_hex(la - 0.06, hex_to_oklch(acc)[1], ha)                                  # pressed: one step darker
    bord = oklch_to_hex(0.33 if dark else 0.89, 0.015, ha)                                     # decorative hairline (exempt)
    bords = nearest_lightness(oklch_to_hex(0.55, 0.02, ha), lambda h: cr(h, surf) >= BOUNDARY + 0.05 and cr(h, bg) >= BOUNDARY + 0.05)
    return dict(bg=bg, surf=surf, surf2=surf2, bord=bord, bordStrong=bords, txt=txt, txt2=txt2, txt3=txt3, acc=acc, accH=acch, onAcc=on), accent

def gates(p, name=None):
    af = SCREEN.get(name, {}).get("acc_floor", TEXT_STRONG)
    pairs = [("txt", "bg", TEXT), ("txt", "surf", TEXT), ("txt", "surf2", TEXT), ("txt2", "bg", TEXT), ("txt2", "surf", TEXT),
             ("txt2", "surf2", TEXT), ("acc", "bg", af), ("acc", "surf", af), ("acc", "surf2", af), ("onAcc", "acc", af),
             ("bordStrong", "surf", BOUNDARY), ("bordStrong", "bg", BOUNDARY)]
    return [(f"{a}/{b}", cr(p[a], p[b]), floor) for a, b, floor in pairs]

# Kept by the owner 2026-10-07; Swiss Blue is the default.
KEPT = ["Swiss Blue", "Daylight", "Tallow & Tome"]

# visual-craft's phone-screen rules (references/mobile-screen-colors.md), applied where the owner
# asked -- Daylight's bright hues: tints at 55% chroma for the margin OEM "Vivid" modes eat, the
# accent's own chroma at 80%, and the accent held to AAA (7:1), the outdoor floor for nav labels.
SCREEN = {"Daylight": {"tint": 0.55, "acc_chroma": 0.80, "acc_floor": TEXT_STRONG}}

def serene_in_the_gap():
    """A new Serene palette (the fourth keyword) in the widest hue gap the kept accents leave,
    at Still Water's own lightness and chroma -- the entry's 'single family plus its neutral'."""
    hues = sorted(hex_to_oklch(LEXICON[k]["light"][1])[2] for k in KEPT)
    gaps = [((hues[(i + 1) % len(hues)] - hues[i]) % 360, hues[i]) for i in range(len(hues))]
    width, start = max(gaps)
    h = (start + width / 2) % 360
    pal = {}
    for mode in ("light", "dark"):
        t, a = LEXICON["Still Water"][mode]
        lt, ct, _ = hex_to_oklch(t); la, ca, _ = hex_to_oklch(a)
        pal[mode] = (oklch_to_hex(lt, ct, h), oklch_to_hex(la, ca, h))
    return h, pal

def score_fourth():
    """Rank every candidate for the fourth slot by its worst-case accent distance (CIEDE2000) to the
    three kept themes, across both modes -- the near-twin problem measured, the higher the better."""
    kept = {k: {m: derive(k, m)[0] for m in ("light", "dark")} for k in KEPT}
    rows = []
    for name in LEXICON:
        if name in KEPT: continue
        cand = {m: derive(name, m)[0] for m in ("light", "dark")}
        worst = min((de(cand[m]["acc"], kept[k][m]["acc"]), k, m) for k in KEPT for m in ("light", "dark"))
        rows.append((worst[0], name, worst[1], worst[2]))
    return sorted(rows, reverse=True)

if __name__ == "__main__":
    gap_hue, LEXICON["Serene Dusk"] = serene_in_the_gap()
    if "--score" in sys.argv:
        print(f"widest gap between the kept accents: hue {gap_hue:.0f}")
        for d, name, k, m in score_fourth():
            print(f"  {name:14} worst accent distance {d:5.1f}  (to {k}, {m})")
        sys.exit(0)
    names = [n for n in LEXICON if "--all" in sys.argv or n in KEPT or n in sys.argv[1:]]
    bad = 0
    out = []
    for name in names:
        for mode in ("light", "dark"):
            p, seed = derive(name, mode)
            g = gates(p, name)
            bad += sum(1 for _, v, f in g if v < f)
            sep = f"surf/bg dE {de(p['surf'], p['bg']):.1f}, surf2/surf dE {de(p['surf2'], p['surf']):.1f}, acc drift dE {de(p['acc'], seed):.1f}"
            print(f"{name} {mode:5}  " + "  ".join(f"{k}={v}" for k, v in p.items()))
            print("      " + "  ".join(f"{n} {v:.2f}{'' if v >= f else ' FAIL'}" for n, v, f in g) + f"   [{sep}]")
            out.append((name, mode, p))
    if "--spec" in sys.argv:                                   # §2.5's token tables and contrast table, in the spec's own form
        label = {"Swiss Blue": "Swiss Blue", "Daylight": "Daylight", "Tallow & Tome": "Tallow & Tome", "Serene Dusk": "Dusk"}
        for name in [n for n in ("Swiss Blue", "Daylight", "Tallow & Tome", "Serene Dusk") if n in names]:
            L = next(p for n, m, p in out if n == name and m == "light"); D = next(p for n, m, p in out if n == name and m == "dark")
            print(f"\n**{label[name]}**\n\n| Token | Light | Dark |\n| --- | --- | --- |")
            for k in ("bg", "surf", "surf2", "bord", "bordStrong", "txt", "txt2", "txt3", "acc", "accH", "onAcc"):
                print(f"| `{k}` | `{L[k].upper()}` | `{D[k].upper()}` |")
        cols = [("txt", "bg"), ("txt", "surf"), ("txt", "surf2"), ("txt2", "bg"), ("txt2", "surf"), ("txt2", "surf2"), ("acc", "bg"), ("acc", "surf"), ("acc", "surf2"), ("onAcc", "acc"), ("bordStrong", "surf"), ("bordStrong", "bg")]
        print("\n| Palette | " + " | ".join(f"`{a}`/`{b}`" for a, b in cols) + " |\n| --- |" + " --- |" * len(cols))
        for name, mode, p in out:
            print(f"| {label.get(name, name)} {mode.capitalize()} | " + " | ".join(f"{cr(p[a], p[b]):.2f}" for a, b in cols) + " |")
    if "--kotlin" in sys.argv:
        for name, mode, p in out:
            ident = name.replace(" & ", "").replace(" ", "")
            ident = ident[0].lower() + ident[1:] + mode.capitalize()
            vals = ", ".join(f'hex("{p[k].lstrip("#").upper()}")' for k in ("bg", "surf", "surf2", "bord", "bordStrong", "txt", "txt2", "txt3", "acc", "accH", "onAcc"))
            print(f"    val {ident} = Palette({vals})")
    print(f"{len(out)} palettes, {bad} failing pair(s)")
    sys.exit(1 if bad else 0)
