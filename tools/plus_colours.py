"""The + buttons' fills and glyphs for pick C (2026-10-09, color-optimization-core).

Rule: a fill is the Oklab blend [share] of the way from the palette's ground to its own accent, so it
stays tied to that palette's Savings figure (drawn in the accent). A first version fixed every fill at
Swiss Blue's lightness; it outshone Savings in the three light themes whose accent is lighter
(design/scratch-2026-10-08/pickC-themes.txt), so it was dropped.
The glyph is whichever of the palette's own roles reads best on the fill (txt, onAcc, bg, surf, acc); where
none reaches the floor, lightness is searched at the accent's hue and chroma (lightness first, the
skill's rule), nearest to that best role. Floor: 4.5:1, the text floor (WCAG asks 3:1 of an icon).
Contrast is designkit's.

    python tools/plus_colours.py           # the table
    python tools/plus_colours.py --kotlin  # the searched glyphs, for ScratchScreens
"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import palette_derive as pd, inversion_score as iv, pick_score as ps

SHARES = {"goal +": 0.40}   # the floating + is the Confirm button (acc / onAcc) since 2026-10-09
ROLES = ("txt", "onAcc", "bg", "surf", "acc")
FLOOR = 4.5

def fill_for(p, share):
    return ps.blend("#" + p["bg"], "#" + p["acc"], share)

def glyph_for(p, fill):
    """(colour, source, contrast): a palette role if one clears the floor, else a lightness search at the accent's hue."""
    role = max(ROLES, key=lambda r: pd.cr("#" + p[r], fill))
    best = "#" + p[role]
    if pd.cr(best, fill) >= FLOOR: return best, role, pd.cr(best, fill)
    L0 = pd.hex_to_oklch(best)[0]; _, C, H = pd.hex_to_oklch("#" + p["acc"])
    ok = [(abs(L / 1000 - L0), pd.oklch_to_hex(L / 1000, C, H)) for L in range(0, 1001)]
    ok = [(d, h) for d, h in ok if pd.cr(h, fill) >= FLOOR]
    if not ok: return best, role + " (none reaches it)", pd.cr(best, fill)
    h = min(ok)[1]
    return h, f"searched, near {role}", pd.cr(h, fill)

def solve():
    pals = iv.palettes(); out = {}
    for name, p in pals.items():
        for what, share in SHARES.items():
            fill = fill_for(p, share)
            glyph, src, c = glyph_for(p, fill)
            out[(name, what)] = (fill, glyph, src, c, max(pd.cr("#" + p["acc"], fill), pd.cr("#" + p["onAcc"], fill)))
    return out

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    out = solve()
    if "--kotlin" in sys.argv:
        for (name, what), (fill, glyph, src, c, _) in out.items():
            if src.startswith("searched"): print(f"    {name} {what}: glyph Color(0xFF{glyph[1:].upper()})")
        return
    print(f"{'palette':16} {'button':11} {'fill':8} {'glyph':8} {'from':22} {'contrast':>8} {'before':>7}")
    for (name, what), (fill, glyph, src, c, before) in out.items():
        print(f"{name:16} {what:11} {fill:8} {glyph:8} {src:22} {c:8.2f} {before:7.2f}{'  BELOW FLOOR' if c < FLOOR else ''}")
    print()
    print(f"worst glyph {min(v[3] for v in out.values()):.2f}:1 (floor {FLOOR})")

if __name__ == "__main__":
    main()
