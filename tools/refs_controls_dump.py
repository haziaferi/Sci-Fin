"""List every clickable node in the reference captures' UI dumps, in dp, for classifying control placement.

    python tools/refs_controls_dump.py [stem ...]
"""
import glob, os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
from refs_score import parse_dump, REFS, DENSITY

def nodes(xml):
    root = parse_dump(xml)
    out = []
    for n in root.iter("node"):
        b = [int(v) for v in re.findall(r"\d+", n.get("bounds"))]
        out.append(dict(cls=n.get("class", "").split(".")[-1], text=(n.get("text") or "").strip(), desc=(n.get("content-desc") or "").strip(),
                        rid=(n.get("resource-id") or "").split("/")[-1], click=n.get("clickable") == "true",
                        x=b[0] / DENSITY, y=b[1] / DENSITY, w=(b[2] - b[0]) / DENSITY, h=(b[3] - b[1]) / DENSITY))
    return out

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    stems = sys.argv[1:] or [os.path.basename(p)[:-4] for p in sorted(glob.glob(os.path.join(REFS, "*.xml")))]
    for s in stems:
        print("==", s)
        for n in nodes(os.path.join(REFS, s + ".xml")):
            if not n["click"]: continue
            label = n["desc"] or n["text"] or n["rid"]
            # the label of a clickable container: its first labelled descendant is not in the flat list, so show the id too
            print(f"  {n['x']:5.0f},{n['y']:5.0f} {n['w']:4.0f}x{n['h']:<4.0f} {n['cls'][:14]:14} {label[:50]!r} {n['rid'][:30]}")

if __name__ == "__main__":
    main()
