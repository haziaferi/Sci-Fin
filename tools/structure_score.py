"""Score the structure-pass renders (2026-10-07) for density and at-a-glance discoverability.

Reads native/shared/build/structure2/<variant>-<mode>-<tab>.png and .json, written by
Structure2RenderTest (density 2, whole pages at 360dp). Every number is measured from the pixels
or the layout probes; the assumptions are the constants named below, stated so they can be argued.

  density        the share of the page's content that fits the first phone screen
                 (VIEWPORT_DP of content above the tab bar) -- more is denser
  grouping       the squint test: blur the page by SQUINT_DP; a section should read as one shape,
                 split from the one above (a background band before its title) and not broken
                 inside (no background band within it)
  entry          the headline figure's share of the screen's conspicuity -- centre-surround local
                 contrast, the Itti-Koch idea: the figure's window against the most conspicuous
                 other window of the same size
  purity         1 / (1 + filled boxes that cannot be tapped): a fill should mean "tap me"
  discoverability = mean(grouping, entry, purity), equal weights -- the one weighting assumption

    python tools/structure_score.py            # the table and the Pareto front
"""
import glob, json, os, sys
from PIL import Image, ImageChops, ImageFilter, ImageStat

DIR = os.path.join(os.path.dirname(__file__), "..", "native", "shared", "build", sys.argv[1] if len(sys.argv) > 1 else "structure2")
PX = 2                 # px per dp in the renders
VIEWPORT_DP = 760 - 64 # a 360x760 phone screen less the tab bar
SQUINT_DP = 6          # the squint blur
BAND_DP = 6            # a background band at least this tall separates two shapes
FOLD_TOP_DP = 0

def lum(img):
    return img.convert("L")

def bg_rows(img, bg):
    """True for every row whose squinted content is indistinguishable from the page ground."""
    w, h = img.size
    blurred = lum(img).filter(ImageFilter.GaussianBlur(SQUINT_DP * PX))
    diff = ImageChops.difference(blurred, Image.new("L", img.size, bg))
    return [diff.crop((0, y, w, y + 1)).getextrema()[1] <= 2 for y in range(h)]

def content_bottom(img, bg):
    w, h = img.size
    L = lum(img)
    for y in range(h - 70 * PX, 0, -1):                      # above the tab bar, which sits at the scene's foot
        lo, hi = L.crop((20, y, w - 20, y + 1)).getextrema()
        if abs(lo - bg) > 4 or abs(hi - bg) > 4:
            return y
    return 0

def bands(flags, min_len):
    out, y = [], 0
    while y < len(flags):
        if flags[y]:
            y0 = y
            while y < len(flags) and flags[y]: y += 1
            if y - y0 >= min_len: out.append((y0, y))
        else:
            y += 1
    return out

def score_frame(stem):
    img = Image.open(stem + ".png").convert("RGB")
    probes = json.load(open(stem + ".json"))
    bg = lum(img).getpixel((2, 300))
    bottom = content_bottom(img, bg)
    density = min(1.0, VIEWPORT_DP * PX / max(1, bottom))
    # sections, in order, from their titles
    titles = sorted({round(p["y"]): p for p in probes if p["name"].startswith("section:")}.values(), key=lambda p: p["y"])
    flags = bg_rows(img, bg)
    gaps = bands(flags, BAND_DP * PX)
    g = []
    for i, t in enumerate(titles):
        top = t["y"]
        end = titles[i + 1]["y"] if i + 1 < len(titles) else bottom
        split_before = any(a < top and b > top - 56 * PX for a, b in gaps)
        inside = [1 for a, b in gaps if a > top + t["h"] and b < end - 56 * PX]
        g.append((1.0 if split_before else 0.0) / (1 + len(inside)))
    grouping = sum(g) / len(g) if g else 0.0
    # entry point: conspicuity of the headline figure against every other window of its size
    fig = next((p for p in probes if p["name"] == "figure"), None)
    entry = 0.0
    if fig:
        L = lum(img).crop((0, 0, img.size[0], bottom + 10))
        surround = L.filter(ImageFilter.GaussianBlur(8 * PX))
        consp = ImageChops.difference(L, surround).filter(ImageFilter.GaussianBlur(4 * PX))
        fx, fy, fw, fh = int(fig["x"]), int(fig["y"]), int(fig["w"]), int(fig["h"])
        mine = ImageStat.Stat(consp.crop((fx, fy, fx + fw, fy + fh))).mean[0]
        best = 0.0
        for y in range(0, consp.size[1] - fh, max(1, fh // 2)):
            for x in range(0, consp.size[0] - fw, max(1, fw // 2)):
                if x < fx + fw and x + fw > fx and y < fy + fh and y + fh > fy: continue
                best = max(best, ImageStat.Stat(consp.crop((x, y, x + fw, y + fh))).mean[0])
        entry = mine / max(mine, best) if mine > 0 else 0.0
    fills = {round(p["y"] / PX) for p in probes if p["name"] == "fill:info"}
    purity = 1.0 / (1 + len(fills))
    return dict(density=density, grouping=grouping, entry=entry, purity=purity, length_dp=bottom / PX)

def main():
    rows = {}
    for png in sorted(glob.glob(os.path.join(DIR, "*-light-budget.png"))):
        v = os.path.basename(png)[: -len("-light-budget.png")]
        frames = [score_frame(os.path.join(DIR, f"{v}-{m}-{t}")) for m in ("light", "dark") for t in ("budget", "activity")]
        avg = {k: sum(f[k] for f in frames) / len(frames) for k in frames[0]}
        avg["discover"] = (avg["grouping"] + avg["entry"] + avg["purity"]) / 3
        rows[v] = avg
    front = [v for v, r in rows.items() if not any(o["density"] >= r["density"] and o["discover"] >= r["discover"] and (o["density"] > r["density"] or o["discover"] > r["discover"]) for o in rows.values())]
    print(f"{'variant':30} density  discover | grouping  entry  purity  length")
    for v, r in sorted(rows.items(), key=lambda kv: -kv[1]["discover"]):
        mark = " *" if v in front else "  "
        print(f"{v:30}{mark} {r['density']:.3f}   {r['discover']:.3f}  |  {r['grouping']:.3f}   {r['entry']:.3f}  {r['purity']:.3f}  {r['length_dp']:.0f}dp")
    print("\n* = on the Pareto front (no other variant is at least as dense and at least as discoverable, and better on one)")
    json.dump(rows, open(os.path.join(DIR, "scores.json"), "w"), indent=1)

if __name__ == "__main__":
    main()
