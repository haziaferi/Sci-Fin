"""The reference-app comparison (2026-10-08): every screen read the same way, from its pixels.

Money figures are found by OCR (Tesseract, word boxes), so the 23 reference captures from the
OnePlus 6T and Sci-Fin's renders at the 6T's own geometry (1080 x 2340, density 2.625) are measured
by one method. All distances are in dp (px / DENSITY).

  key figures  money values at least as tall as the screen's median money value -- the figures a
               person reads, not chart axis labels
  alignment    the owner's criterion (2026-10-08): the share of key figures whose left or right edge
               lines up, within ALIGN_DP, with another key figure on the same screen
  facts        money values on the first screen, below the status bar and above the navigation bar
  sizes        distinct heights among the key figures (4dp steps) -- how many figure sizes compete

    python tools/refs_score.py            # the table
    python tools/refs_score.py --control  # OCR boxes against the uiautomator bounds, where the dump has them
"""
import glob, os, re, subprocess, sys, json
import xml.etree.ElementTree as ET
from PIL import Image, ImageOps


def parse_dump(path):
    """A uiautomator dump carries third-party apps' text: refuse DTDs (XXE, entity expansion) before parsing."""
    raw = open(path, encoding="utf-8").read()
    if "<!DOCTYPE" in raw or "<!ENTITY" in raw:
        raise ValueError(f"{path}: a DTD in a UI dump -- refused")
    return ET.fromstring(raw)

HERE = os.path.dirname(os.path.abspath(__file__))
REFS = os.path.join(HERE, "..", "design", "refs-apps")
SCIFIN = os.path.join(HERE, "..", "native", "shared", "build", os.environ.get("SCIFIN_DIR", "structure5"))
TESS = r"C:\Program Files\Tesseract-OCR\tesseract.exe"
DENSITY = 2.625
ALIGN_DP = 4
TOP_DP, BOTTOM_DP = 30, 56            # status bar, navigation bar

MONEY = re.compile(r"^[-−+]?[€$]?[-−]?\d{1,3}(?:[.,\u00a0 ]\d{3})*(?:[.,]\d{1,2})?[€$]?$")

def is_money(t):
    t = t.strip().replace("\u202f", "").replace("\u00a0", "").replace(" ", "")
    if not MONEY.match(t) or not re.search(r"\d", t): return False
    return bool(re.search(r"[€$]", t) or re.search(r"[.,]\d{2}$", t) or re.search(r"\d[.,]\d{3}", t))

def ocr_words(png):
    img = Image.open(png).convert("L")
    if sum(img.resize((40, 80)).tobytes()) / 3200 < 110:      # light on dark: invert for Tesseract
        img = ImageOps.invert(img)
    tmp = os.path.join(os.environ.get("TEMP", "."), "ocr_in.png"); img.save(tmp)
    out = subprocess.run([TESS, tmp, "stdout", "--psm", "11", "-l", "eng", "tsv"], capture_output=True, text=True, encoding="utf-8").stdout
    words = []
    for line in out.splitlines()[1:]:
        f = line.split("\t")
        if len(f) < 12 or not f[11].strip() or float(f[10]) < 30: continue
        x, y, w, h = map(int, f[6:10]); words.append(dict(x=x, y=y, w=w, h=h, t=f[11].strip(), line=(f[2], f[3], f[4])))
    return words, Image.open(png).size

def merge_symbols(words):
    """'€' + '1.450,00' printed as two words on one line read as one figure."""
    out = []
    for w in sorted(words, key=lambda w: (w["line"], w["x"])):
        if out and out[-1]["line"] == w["line"] and w["x"] - (out[-1]["x"] + out[-1]["w"]) < w["h"] * 0.6 and \
           (out[-1]["t"] in "€$" or w["t"] in "€$EUR" or w["t"] == "EUR"):
            p = out[-1]; x2 = max(p["x"] + p["w"], w["x"] + w["w"])
            p.update(t=p["t"] + w["t"], w=x2 - p["x"], h=max(p["h"], w["h"])); continue
        out.append(dict(w))
    return out

def figures(png):
    words, (W, H) = ocr_words(png)
    figs = [w for w in merge_symbols(words) if is_money(w["t"].replace("EUR", ""))
            and w["y"] / DENSITY > TOP_DP and (w["y"] + w["h"]) / DENSITY < H / DENSITY - BOTTOM_DP]
    return figs, H

def score(png):
    figs, H = figures(png)
    if not figs: return dict(facts=0, key=0, alignment=None, sizes=0)
    hs = sorted(f["h"] for f in figs); med = hs[len(hs) // 2]
    key = [f for f in figs if f["h"] >= med]
    tol = ALIGN_DP * DENSITY
    aligned = [f for f in key if any(g is not f and (abs(f["x"] - g["x"]) <= tol or abs(f["x"] + f["w"] - g["x"] - g["w"]) <= tol) for g in key)]
    sizes = len({round(f["h"] / DENSITY / 4) for f in key})
    return dict(facts=len(figs), key=len(key), alignment=len(aligned) / len(key) if len(key) > 1 else None, sizes=sizes,
                figs=[(f["t"], round(f["x"] / DENSITY), round(f["y"] / DENSITY), round(f["h"] / DENSITY)) for f in key])

def dump_money(xml):
    out = []
    for n in parse_dump(xml).iter("node"):
        t = (n.get("text") or n.get("content-desc") or "").strip()
        b = [int(v) for v in re.findall(r"\d+", n.get("bounds"))]
        if t and is_money(t.replace("EUR", "").replace(" ", "")) and b[2] > b[0]: out.append((t, b))
    return out

def control():
    """Where the dump has exact bounds per figure, does OCR find the same figures in the same place?"""
    for stem in ["monefy-home", "moneymanager-transactions", "myexpenses-transactions", "mmex-home", "goodbudget-envelopes"]:
        png = os.path.join(REFS, stem + ".png"); xml = os.path.join(REFS, stem + ".xml")
        truth = dump_money(xml); figs, _ = figures(png)
        found = 0; dx = []
        for t, b in truth:
            near = [f for f in figs if abs(f["x"] - b[0]) < 60 * DENSITY and abs(f["y"] - b[1]) < 20 * DENSITY]
            if near:
                found += 1; f = near[0]; dx.append(min(abs(f["x"] - b[0]), abs(f["x"] + f["w"] - b[2])) / DENSITY)
        print(f"{stem:28} dump figures {len(truth):2}  found by OCR {found:2}  edge error median {sorted(dx)[len(dx)//2] if dx else float('nan'):.1f}dp")

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    if "--control" in sys.argv: return control()
    rows = []
    for png in sorted(glob.glob(os.path.join(REFS, "*.png"))) + sorted(glob.glob(os.path.join(SCIFIN, "*.png"))):
        name = os.path.basename(png)[:-4]
        if name in ("contact",): continue
        r = score(png); r["name"] = ("SCI-FIN " if SCIFIN in png else "") + name; rows.append(r)
    print(f"{'screen':40} facts  key  aligned  sizes")
    for r in rows:
        a = "  -  " if r["alignment"] is None else f"{r['alignment']*100:4.0f}%"
        print(f"{r['name']:40} {r['facts']:5} {r['key']:4}  {a}   {r['sizes']}")
    json.dump(rows, open(os.path.join(REFS, "scores.json"), "w", encoding="utf-8"), indent=1, ensure_ascii=False)

if __name__ == "__main__":
    main()
