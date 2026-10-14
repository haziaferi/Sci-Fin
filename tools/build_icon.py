"""Sci-Fin's launcher icon from the user's Affinity lettering (Metamorphous S + F, outlined by
Inkscape): one 1024 geometry sized to the adaptive icon's safe zone, then the Android adaptive
icon (vector foreground + colour background + monochrome), the desktop window PNG, the MSI's ICO,
and the design/ sources. Small Windows sizes get a size-dependent outward stroke so the face's
hairlines survive 16-32px."""
import re, subprocess, pathlib, io
from PIL import Image, ImageDraw

D = pathlib.Path(__file__).resolve().parent.parent          # the repository root
ICON = D / "design" / "icon"; PNG = ICON / "png"
AND = D / "native" / "androidApp" / "src" / "main" / "res"
DESK = D / "native" / "desktopApp"
ACC, ON = "#5B6670", "#FFFFFF"
ink = r"C:\Program Files\Inkscape\bin\inkscape.com"

src = (ICON / "lettering-paths.svg").read_text(encoding="utf-8")
paths = re.findall(r'<path[^>]*\sd="([^"]+)"', src)
assert len(paths) == 2
# union bbox of the drawn objects, read from `inkscape --query-all` on every run (it was typed in
# from one query, so an edited lettering file kept the old box; audit 2026-09-25). The first row
# is the document root; the rest are the two glyphs, S and F.
rows = subprocess.run([ink, "--query-all", str(ICON / "lettering-paths.svg")], check=True, capture_output=True, text=True).stdout.split()
boxes = [tuple(float(v) for v in r.split(",")[1:]) for r in rows[1:]]
assert len(boxes) == 2, rows
x0, y0 = min(b[0] for b in boxes), min(b[1] for b in boxes)
x1, y1 = max(b[0] + b[2] for b in boxes), max(b[1] + b[3] for b in boxes)
w, h = x1 - x0, y1 - y0
# the adaptive safe zone is a 66/108 circle = 626 of 1024; the mark's height takes 520 of it (55 of the 66dp)
FIT_H = 520
fit = FIT_H / h
tx, ty = (1024 - w * fit) / 2 - x0 * fit, (1024 - h * fit) / 2 - y0 * fit

def glyphs(stroke=0.0):
    st = f' stroke="{ON}" stroke-width="{stroke / fit:.2f}" stroke-linejoin="round"' if stroke else ""
    return (f'<g transform="translate({tx:.3f} {ty:.3f}) scale({fit:.6f})" fill="{ON}"{st}>'
            + "".join(f'<path d="{d}"/>' for d in paths) + "</g>")

def svg(body, tile=True, rounded=False):
    rx = ' rx="225"' if rounded else ""           # Windows 11 draws its own tile: ~22% corners
    bg = f'<rect width="1024" height="1024"{rx} fill="{ACC}"/>' if tile else ""
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">{bg}{body}</svg>'

# design sources: the icon as decided, and its foreground alone
(ICON / "sci-fin-icon.svg").write_text(svg(glyphs()), encoding="utf-8")
(ICON / "sci-fin-icon-fg.svg").write_text(svg(glyphs(), tile=False), encoding="utf-8")

def render(svg_text, size, out):
    tmp = PNG / "_tmp.svg"; tmp.write_text(svg_text, encoding="utf-8")
    subprocess.run([ink, str(tmp), "--export-type=png", f"--export-width={size}", f"--export-filename={out}"],
                   check=True, capture_output=True)
    tmp.unlink()

# Windows: the ICO carries 16..256, each rendered with the stroke the size needs (in 1024 units)
STROKE = {16: 26, 24: 18, 32: 13, 48: 8, 64: 5, 128: 2, 256: 0}
frames = []
for size, stroke in STROKE.items():
    out = PNG / f"win-{size}.png"
    render(svg(glyphs(stroke), rounded=True), size, out)
    frames.append(Image.open(out).convert("RGBA"))
(DESK / "icon").mkdir(exist_ok=True)
frames[-1].save(DESK / "icon" / "sci-fin.ico", format="ICO", sizes=[(s, s) for s in STROKE], append_images=frames[:-1])
# the running window gets the same frames as classpath resources (Main.kt hands the JFrame the whole
# ladder, since Compose's Window(icon=) would render one 192px image and let Windows downscale it)
res = DESK / "src" / "main" / "resources" / "icon"; res.mkdir(parents=True, exist_ok=True)
for size in STROKE: (res / f"win-{size}.png").write_bytes((PNG / f"win-{size}.png").read_bytes())

# Android: adaptive icon, viewport 1024 so the geometry is shared verbatim; a 1024-unit stroke of 6
# (~0.6 dp on the 108 dp canvas) firms the hairlines at launcher sizes without changing the shape
AND.mkdir(parents=True, exist_ok=True)
(AND / "values").mkdir(exist_ok=True); (AND / "drawable").mkdir(exist_ok=True); (AND / "mipmap-anydpi-v26").mkdir(exist_ok=True)
(AND / "values" / "ic_launcher_background.xml").write_text(
    '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <!-- the Slate accent (§2.5): the icon does not follow the chosen theme -->\n'
    f'    <color name="ic_launcher_background">{ACC}</color>\n</resources>\n', encoding="utf-8")
vd = ('<?xml version="1.0" encoding="utf-8"?>\n'
      '<!-- Sci-Fin\'s mark (Decided 2026-09-15): the user\'s Metamorphous S + F lettering, outlined; the\n'
      '     source is design/icon/lettering-paths.svg and this file is generated from it, not edited. -->\n'
      '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
      '    android:width="108dp"\n    android:height="108dp"\n    android:viewportWidth="1024"\n    android:viewportHeight="1024">\n'
      f'    <group\n        android:translateX="{tx:.3f}"\n        android:translateY="{ty:.3f}"\n'
      f'        android:scaleX="{fit:.6f}"\n        android:scaleY="{fit:.6f}">\n')
for d in paths:
    vd += (f'        <path\n            android:fillColor="{ON}"\n            android:strokeColor="{ON}"\n'
           f'            android:strokeWidth="{6 / fit:.2f}"\n            android:strokeLineJoin="round"\n'
           f'            android:pathData="{d}" />\n')
vd += "    </group>\n</vector>\n"
(AND / "drawable" / "ic_launcher_foreground.xml").write_text(vd, encoding="utf-8")
adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@color/ic_launcher_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '    <!-- Android 13+ themed icons: the same mark, tinted by the launcher -->\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_foreground" />\n'
            '</adaptive-icon>\n')
for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    (AND / "mipmap-anydpi-v26" / name).write_text(adaptive, encoding="utf-8")

# proof sheet: the mark under a circle mask and a squircle at 108/72 dp proportions, and the ICO frames
render(svg(glyphs()), 512, PNG / "final-512.png")
big = Image.open(PNG / "final-512.png").convert("RGBA")
vis = 512 * 72 // 108                                  # the launcher shows 72 of the 108 dp
crop = big.resize((int(512 * 108 / 72),) * 2, Image.LANCZOS)  # scale so the visible 72dp window is 512
off = (crop.width - 512) // 2; crop = crop.crop((off, off, off + 512, off + 512))
sheet = Image.new("RGBA", (1100, 860), (138, 138, 138, 255))
for i, shape in enumerate(("circle", "squircle")):
    mask = Image.new("L", (512, 512), 0); dr = ImageDraw.Draw(mask)
    if shape == "circle": dr.ellipse((0, 0, 511, 511), fill=255)
    else: dr.rounded_rectangle((0, 0, 511, 511), radius=140, fill=255)
    m = Image.new("RGBA", (512, 512), (0, 0, 0, 0)); m.paste(crop, (0, 0), mask)
    sheet.alpha_composite(m, (20 + i * 540, 20))
x = 20
for size in STROKE:
    f = Image.open(PNG / f"win-{size}.png").convert("RGBA"); sheet.alpha_composite(f, (x, 830 - size)); x += size + 16
sheet.save(PNG / "final-proof.png")
print("ok fit", round(fit, 5), "tx", round(tx, 1), "ty", round(ty, 1))
