"""Install one app through Droidify or Aurora on the connected phone (2026-10-07 comparison).

    python tools/phone/store_install.py droidify "Search text" "Result title" package.name
    python tools/phone/store_install.py aurora   "Search text" "Result title" package.name

Opens the store, searches, opens the result whose title contains "Result title", reads the size,
logs store/name/version/size to design/refs-apps/installs.log, skips anything over CAP_MB, taps
Install and waits until the package is present. The owner approved these installs on 2026-10-07.
"""
import os, re, subprocess, sys, time
sys.path.insert(0, os.path.dirname(__file__))
import ui

CAP_MB = 150
LOG = os.path.join(ui.ROOT, "installs.log")
STORES = {"droidify": "com.looker.droidify", "aurora": "com.aurora.store"}

def installed(pkg):
    return ("package:" + pkg) in ui.adb("shell", "pm", "list", "packages", pkg).split()

def texts():
    return [n for n in ui.nodes(ui.hierarchy()) if n["text"] or n["desc"]]

def tap_node(n):
    x, y = ui.centre(n["bounds"]); ui.adb("shell", "input", "tap", str(x), str(y))

def first(pred, tries=8, wait=1.5):
    for _ in range(tries):
        hit = [n for n in texts() if pred(n)]
        if hit: return hit
        time.sleep(wait)
    return []

def main(store, query, title, pkg):
    sys.stdout.reconfigure(encoding="utf-8")
    if installed(pkg):
        print("already installed", pkg); return
    ui.adb("shell", "input", "keyevent", "KEYCODE_HOME"); time.sleep(1)
    if store == "aurora":
        # Aurora opens an app's own page from a market:// link -- no search, no wrong result
        ui.adb("shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", f"market://details?id={pkg}", "-p", STORES[store]); time.sleep(3)
        ok = first(lambda n: (n["text"] or n["desc"]) in ("Apri", "Open"), tries=3)   # "open Aurora from an external app?" -- our own link
        if ok: tap_node(ok[0]); time.sleep(6)
        return install_from_page(store, title, pkg)
    ui.main(["open", STORES[store]]); time.sleep(4)
    if store == "droidify":
        hit = first(lambda n: n["desc"] in ("Cerca", "Search") or n["text"] in ("Cerca", "Search"))
        if hit: tap_node(hit[0]); time.sleep(1)
        clear = [n for n in texts() if n["desc"] in ("Cancella query", "Clear query")]
        if clear: tap_node(clear[0]); time.sleep(0.5)
    else:
        hit = first(lambda n: "cerca" in (n["text"] + n["desc"]).lower() or "search" in (n["text"] + n["desc"]).lower())
        if not hit: raise SystemExit("aurora: no search field")
        tap_node(hit[0]); time.sleep(1.5)
    # empty whatever the field still holds from the last search, then type
    field = [n for n in ui.nodes(ui.hierarchy()) if n["cls"].endswith("EditText")]
    if field: tap_node(field[0]); time.sleep(0.5)
    ui.adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    ui.adb("shell", "input", "keyevent", *(["KEYCODE_DEL"] * 40))
    ui.main(["type", query]); ui.main(["key", "ENTER"]); time.sleep(4)
    res = first(lambda n: title.lower() in (n["text"] + n["desc"]).lower() and n["bounds"][1] > 400)
    if not res: raise SystemExit(f"{store}: no result titled {title!r}")
    tap_node(res[0]); time.sleep(4)
    return install_from_page(store, title, pkg)

def install_from_page(store, title, pkg):
    page = " | ".join((n["text"] or n["desc"]) for n in texts())
    size = re.search(r"(\d+(?:[.,]\d+)?)\s*(MB|GB|kB|KB)", page)
    mb = None
    if size:
        v = float(size.group(1).replace(",", ".")); mb = v * 1024 if size.group(2) == "GB" else v / 1024 if size.group(2).lower() == "kb" else v
    ver = re.search(r"(?:Versione|Version)\s*\|\s*([\w.\-]+)", page)
    line = f"{time.strftime('%Y-%m-%dT%H:%M:%S')} {store:8} {title} ({pkg}) {ver.group(1) if ver else '?'}  {f'{mb:.1f} MB' if mb is not None else 'size not shown'}"
    if mb is not None and mb > CAP_MB:
        open(LOG, "a", encoding="utf-8").write(line + "  SKIPPED (over cap)\n"); print("skipped", line); return
    btn = first(lambda n: (n["text"] or n["desc"]) in ("Installa", "Install", "Scarica", "Download") and n["click"], tries=4)
    if not btn:
        btn = first(lambda n: (n["text"] or n["desc"]) in ("Installa", "Install", "Scarica", "Download"), tries=2)
    if not btn: raise SystemExit(f"{store}: no install button; page: {page[:300]}")
    open(LOG, "a", encoding="utf-8").write(line + "\n")
    tap_node(btn[0])
    for i in range(48):
        time.sleep(5)
        if installed(pkg):
            print(f"installed {pkg} after {(i + 1) * 5}s  [{line}]"); return
    raise SystemExit(f"not installed after 4 min: {pkg}; screen: " + " | ".join((n['text'] or n['desc']) for n in texts())[:300])

if __name__ == "__main__":
    main(*sys.argv[1:5])
