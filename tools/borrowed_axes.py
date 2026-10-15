"""Axes borrowed from other projects (2026-10-14), run on MeasureRenderTest's renders.

The catalogue of what other projects measure is design/axis-catalogue-2026-10-14.md; these are the ones today's
renders can feed and Sci-Fin either lacks or measured only on render folders the 2026-10-11 tidy recycled. Each keeps
its source's formula and constants; where Sci-Fin's geometry differs, the change is named.

Input: native/shared/build/measure/<platform>_<win>_<fs>_<density>_<view>.png and measure.jsonl, written by
MeasureRenderTest with SCIFIN_MEASURE=1 (density 1, so px = dp; phone frames are win x 900, desktop win x 760).

  clutter     share of pixels on an edge: grey, PIL FIND_EDGES, >= 40/255, over all pixels -- lower is calmer.
              Factotum tools/screens/screen_score.py:152 (Mack & Oliva 2004). Ties under 0.002. Factotum averaged
              light and dark; these renders are the default theme only. Reference apps measured 0.011-0.079.
  add_mass    the Add control's share of the screen's visual mass: mass = sum |luma - ground| over a box, ground =
              the modal luma among 32 bins. Factotum tools/screens/sheet_weight.py:45 (there over the sheet; here
              over the page). Reads how loudly Add speaks against everything else.
  add_reach   Fitts ID from the thumb's rest to Add, log2(dist / min(w, h) + 1), phone only.
              Factotum tools/screens/capture_opt.py:82. Rest point (w/2, H - 40): Factotum's (180, 700) on a 740
              frame is 40 dp above the bottom, kept as that offset on Sci-Fin's 900 frame.
  add_spread  how far Add moves when only the text scale and density change, pstdev(x) + pstdev(y) of its centre
              over the 3 text scales x 2 densities at one window width -- lower is steadier. PaperKnife
              _qt-2026-10-09/phase9/score_layout.py:181 (there over short/typical/long file names). Also its
              consistency score lin(max deviation from the median, 120) and the defect line at 16 px
              (_qt-2026-10-09/phase6/after/measure.py:1111, lin at :655).
  small_taps  probed tappables (el:*; round:* probes are drawn circles, not hit boxes) under the target: 48 dp on the phone, 40 on the desktop -- the
              targetDp MeasureRenderTest itself passes. Factotum screen_score.py:173 used 48 everywhere.
  overflow    rail labels that overflow (the probe's own flag). PaperKnife clipping_overflow_defects counts the
              same defect from widget geometry.

    python tools/borrowed_axes.py              # score every frame, write design/borrowed-axes-2026-10-14.tsv
    python tools/borrowed_axes.py --selftest   # each formula against a hand-computed case
"""
import collections, json, math, os, statistics, sys
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
DIR = os.path.join(HERE, "..", "native", "shared", "build", "measure")
OUT = os.path.join(HERE, "..", "design", "borrowed-axes-2026-10-14.tsv")
EDGE_T = 40            # Factotum's edge threshold, of 255
CLUTTER_TIE = 0.002
LUMA_BINS = 32
THUMB_UP_DP = 40       # the thumb rests this far above the frame's foot
LANDING_SCALE = 120    # PaperKnife: lin() reaches 0 at this deviation
LANDING_DEFECT = 16    # PaperKnife: a deviation above this is a defect
TARGET = {"phone": 48, "desktop": 40}
ADD = ("el:fab", "el:add:")


def edge_density(img, threshold=EDGE_T, inner=False):
    """Factotum's measure as written. PIL leaves FIND_EDGES' outermost pixel ring unfiltered, so a light page's
    frame counts as edge: +2(W+H)/(W*H), 0.0078 at 360 x 900 -- constant across frames of one size, not across
    sizes. inner=True drops that ring (for comparing widths); False keeps Factotum's scale and reference values."""
    im = img.convert("L").filter(ImageFilter.FIND_EDGES)
    if inner:
        im = im.crop((1, 1, im.size[0] - 1, im.size[1] - 1))
    return sum(im.histogram()[threshold:]) / float(im.size[0] * im.size[1])


def ground(lum):
    h256 = lum.histogram()
    hist = [sum(h256[b * 256 // LUMA_BINS:(b + 1) * 256 // LUMA_BINS]) for b in range(LUMA_BINS)]
    b = max(range(LUMA_BINS), key=hist.__getitem__)
    return (b + 0.5) * 256 / LUMA_BINS


def mass(lum, g, box=None):
    region = lum.crop(tuple(int(round(v)) for v in box)) if box else lum
    return sum(n * abs(v - g) for v, n in enumerate(region.histogram()))


def fitts(rest, box):
    x, y, w, h = box
    return math.log2(math.dist(rest, (x + w / 2, y + h / 2)) / min(w, h) + 1)


def lin(x, scale):
    return 100 * max(0.0, 1 - x / scale)


def load():
    rows = [json.loads(l) for l in open(os.path.join(DIR, "measure.jsonl"), encoding="utf-8") if l.strip()]
    frames = collections.defaultdict(list)
    for r in rows:
        frames[(r["platform"], r["win"], r["fs"], r["density"], r["view"])].append(r)
    return frames


def png_of(key):
    p, w, fs, d, v = key
    return os.path.join(DIR, f"{p}_{w}_{fs}_{d}_{v}.png")


def score_frame(key, probes):
    img = Image.open(png_of(key))
    lum = img.convert("L")
    g = ground(lum)
    total = mass(lum, g) or 1.0
    adds = [r for r in probes if r["name"].startswith(ADD)]
    # round:* probes report the DRAWN circle; its hit box is the larger `reach` (Controls.kt:101-105, checked by
    # RoundButtonTest), so they are left out rather than counted small by construction
    taps = [r for r in probes if r["name"].startswith("el:") and "w" in r]
    t = TARGET[key[0]]
    W, H = img.size
    out = dict(clutter=round(edge_density(img), 4), clutter_inner=round(edge_density(img, inner=True), 4),
               small_taps=sorted({f'{r["name"]} {r["w"]:.0f}x{r["h"]:.0f}' for r in taps if r["w"] < t or r["h"] < t}),
               overflow=sum(1 for r in probes if r["name"] == "rail-label" and r.get("overflow", 0) > 0),
               adds={})
    for r in adds:
        box = (r["x"], r["y"], r["w"], r["h"])
        a = dict(at=(r["x"] + r["w"] / 2, r["y"] + r["h"] / 2),
                 mass=round(mass(lum, g, (r["x"], r["y"], r["x"] + r["w"], r["y"] + r["h"])) / total, 4))
        if key[0] == "phone":
            a["reach"] = round(fitts((W / 2, H - THUMB_UP_DP), box), 2)
        out["adds"][r["name"]] = a
    return out


def spread(points):
    xs, ys = [p[0] for p in points], [p[1] for p in points]
    mx, my = statistics.median(xs), statistics.median(ys)
    dev = max(math.dist(p, (mx, my)) for p in points)
    return statistics.pstdev(xs) + statistics.pstdev(ys), dev


def selftest():
    # edge density: a 10x10 black square on white, 100x100. Inside the frame FIND_EDGES marks the square's two
    # light side only: the ring just outside it, 12x12 - 10x10 = 44 px (the dark side's response is negative and
    # clips to 0 -- so the measure counts each edge once, on its bright side); the unfiltered white frame adds 396
    im = Image.new("L", (100, 100), 255)
    im.paste(0, (45, 45, 55, 55))
    ring = edge_density(im, inner=True) * 98 * 98
    assert abs(ring - 44) < 1e-6, ring
    assert abs(edge_density(im) * 10000 - ring - 396) < 1e-6, edge_density(im)
    # ground and mass: white page with one 10x10 black box -> ground bin 31 (centre 252), box mass 100 * 252
    lum = im
    assert ground(lum) == 252.0
    assert mass(lum, 252.0, (45, 45, 55, 55)) == 100 * 252.0
    # Fitts: rest (180, 860), a 48 dp target centred 200 dp straight up -> log2(200/48 + 1)
    assert abs(fitts((180, 860), (156, 636, 48, 48)) - math.log2(200 / 48 + 1)) < 1e-9
    # spread: points at x = 10, 10, 13 and y fixed -> pstdev 1.414..., max deviation from the median 3
    s, d = spread([(10, 0), (10, 0), (13, 0)])
    assert abs(s - statistics.pstdev([10, 10, 13])) < 1e-9 and d == 3
    assert lin(0, 120) == 100 and lin(60, 120) == 50 and lin(500, 120) == 0
    print("selftest: 6 checks of 5 formulas agree with the hand-computed cases")


def main():
    if "--selftest" in sys.argv:
        return selftest()
    frames = load()
    scored = {k: score_frame(k, v) for k, v in sorted(frames.items()) if os.path.exists(png_of(k))}
    lines = ["platform\twin\tfs\tdensity\tview\tclutter\tclutter_inner\tsmall_taps\toverflow\tadd\tadd_mass\tadd_reach"]
    for (p, w, fs, d, v), s in scored.items():
        adds = s["adds"] or {"-": {}}
        for n, a in adds.items():
            lines.append(f"{p}\t{w}\t{fs}\t{d}\t{v}\t{s['clutter']}\t{s['clutter_inner']}\t{len(s['small_taps'])}\t{s['overflow']}\t{n}\t"
                         f"{a.get('mass', '')}\t{a.get('reach', '')}")
    open(OUT, "w", encoding="utf-8").write("\n".join(lines) + "\n")

    print(f"{len(scored)} frames -> {os.path.relpath(OUT, os.path.join(HERE, '..'))}\n")
    print("clutter (edge share; lower is calmer; ties under 0.002) -- mean over text scales and densities")
    by = collections.defaultdict(list)
    for (p, w, fs, d, v), s in scored.items():
        by[(p, v)].append((s["clutter"], s["clutter_inner"]))
    for (p, v), c in sorted(by.items()):
        a, b = [x[0] for x in c], [x[1] for x in c]
        print(f"  {p:8s} {v:9s} {statistics.mean(a):.4f} (range {min(a):.4f}-{max(a):.4f})"
              f"   without the frame ring {statistics.mean(b):.4f}")

    print("\nAdd: mass share, phone reach (bits), and how far it moves across text scale x density at one width")
    pts = collections.defaultdict(list)
    for (p, w, fs, d, v), s in scored.items():
        for n, a in s["adds"].items():
            pts[(p, w, v, n)].append((a["at"], a["mass"], a.get("reach")))
    for (p, w, v, n), xs in sorted(pts.items()):
        sp, dev = spread([x[0] for x in xs])
        reach = [x[2] for x in xs if x[2] is not None]
        print(f"  {p:8s} {w:5d} {n:22s} mass {statistics.mean(x[1] for x in xs):.4f}"
              + (f"  reach {statistics.mean(reach):.2f}" if reach else "")
              + f"  spread {sp:5.1f}  consistency {lin(dev, LANDING_SCALE):5.1f}" + ("  DEFECT" if dev > LANDING_DEFECT else ""))

    small = collections.Counter(t.rsplit(" ", 1)[0] + f" ({p})" for (p, *_), s in scored.items() for t in s["small_taps"])
    print(f"\nsmall taps (phone < {TARGET['phone']} dp, desktop < {TARGET['desktop']} dp): "
          + (", ".join(f"{k} x{c}" for k, c in small.most_common()) or "none"))
    print(f"rail labels overflowing: {sum(s['overflow'] for s in scored.values())}")


if __name__ == "__main__":
    main()
