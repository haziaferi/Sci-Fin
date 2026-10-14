"""Drive the connected phone through adb and uiautomator (2026-10-07, the reference-app comparison).

    python tools/phone/ui.py dump [name]          # save the hierarchy + a screenshot under design/refs-apps/<name>
    python tools/phone/ui.py find "text"          # list nodes whose text/desc contains it, with centres
    python tools/phone/ui.py tap "text" [n]       # tap the n-th node (default 0) whose text/desc contains it
    python tools/phone/ui.py tapxy X Y
    python tools/phone/ui.py type "text"          # type into the focused field
    python tools/phone/ui.py key BACK|ENTER|HOME
    python tools/phone/ui.py open <package>
    python tools/phone/ui.py fg                   # the foreground package
"""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "design", "refs-apps")
ENV = dict(os.environ, MSYS_NO_PATHCONV="1")

def adb(*a, binary=False):
    r = subprocess.run(["adb", *a], capture_output=True, env=ENV)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")

def hierarchy():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        x = adb("shell", "cat", "/sdcard/ui.xml")
        if x.strip().startswith("<?xml"):
            return x
        time.sleep(1)
    raise SystemExit("uiautomator dump failed")

def nodes(xml):
    out = []
    if "<!DOCTYPE" in xml or "<!ENTITY" in xml:     # third-party apps' text: refuse DTDs before parsing
        raise ValueError("a DTD in a UI dump -- refused")
    for n in ET.fromstring(xml).iter("node"):
        b = [int(v) for v in re.findall(r"\d+", n.get("bounds", "[0,0][0,0]"))]
        out.append(dict(text=n.get("text", ""), desc=n.get("content-desc", ""), cls=n.get("class", ""), res=n.get("resource-id", ""),
                        click=n.get("clickable") == "true", bounds=b))
    return out

def match(q):
    q = q.lower()
    return [n for n in nodes(hierarchy()) if q in n["text"].lower() or q in n["desc"].lower()]

def centre(b): return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2

def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")
    cmd = argv[0]
    if cmd == "dump":
        name = argv[1] if len(argv) > 1 else time.strftime("%H%M%S")
        os.makedirs(ROOT, exist_ok=True)
        open(os.path.join(ROOT, name + ".xml"), "w", encoding="utf-8").write(hierarchy())
        open(os.path.join(ROOT, name + ".png"), "wb").write(adb("exec-out", "screencap", "-p", binary=True))
        print("saved", name)
    elif cmd == "find":
        for n in match(argv[1]):
            print(centre(n["bounds"]), repr(n["text"] or n["desc"])[:70], n["cls"].split(".")[-1], "click" if n["click"] else "")
    elif cmd == "tap":
        hits = match(argv[1]); i = int(argv[2]) if len(argv) > 2 else 0
        if not hits: raise SystemExit(f"no node with {argv[1]!r}")
        x, y = centre(hits[i]["bounds"]); adb("shell", "input", "tap", str(x), str(y)); print("tapped", x, y, repr(hits[i]["text"] or hits[i]["desc"])[:50])
    elif cmd == "tapxy":
        adb("shell", "input", "tap", argv[1], argv[2])
    elif cmd == "type":
        adb("shell", "input", "text", argv[1].replace(" ", "%s"))
    elif cmd == "key":
        adb("shell", "input", "keyevent", "KEYCODE_" + argv[1])
    elif cmd == "open":
        adb("shell", "monkey", "-p", argv[1], "-c", "android.intent.category.LAUNCHER", "1")
    elif cmd == "fg":
        print(re.search(r"mCurrentFocus=\S+ \S+ (\S+)/", adb("shell", "dumpsys", "window")).group(1))
    elif cmd == "keys":                       # tap on-screen keypad keys by their exact label, one per character
        ns = nodes(hierarchy())
        for ch in argv[1]:
            hit = sorted([n for n in ns if (n["text"] or n["desc"]).strip() == ch], key=lambda n: not n["click"])
            if not hit: raise SystemExit(f"no key {ch!r}")
            x, y = centre(hit[0]["bounds"]); adb("shell", "input", "tap", str(x), str(y)); time.sleep(0.25)
    elif cmd == "tapexact":
        hit = [n for n in nodes(hierarchy()) if (n["text"] or n["desc"]).strip() == argv[1]]
        i = int(argv[2]) if len(argv) > 2 else 0
        if not hit: raise SystemExit(f"no node exactly {argv[1]!r}")
        x, y = centre(hit[i]["bounds"]); adb("shell", "input", "tap", str(x), str(y)); print("tapped", x, y)
    elif cmd == "look":
        print(look())
    elif cmd == "texts":
        for n in nodes(hierarchy()):
            if n["text"] or n["desc"]: print(n["bounds"], repr(n["text"] or n["desc"])[:60], "click" if n["click"] else "")



def look(path=None):
    """A small screenshot for reading the screen by eye, beside the UI dump."""
    from PIL import Image
    import io as _io
    path = path or os.path.join(os.environ.get("TEMP", "."), "phone_look.png")
    im = Image.open(_io.BytesIO(adb("exec-out", "screencap", "-p", binary=True)))
    im.resize((im.width // 3, im.height // 3)).save(path)
    return path


if __name__ == "__main__":
    main(sys.argv[1:])
