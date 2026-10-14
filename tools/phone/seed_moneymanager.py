"""Money Manager (Realbyte): the comparison's sample data (2026-10-07). Each step reads the screen it acts on."""
import os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
import ui

def visible(label):
    return any((n["text"] or n["desc"]).strip().endswith(label) for n in ui.nodes(ui.hierarchy()))

def tap_ends(label):
    hit = [n for n in ui.nodes(ui.hierarchy()) if (n["text"] or n["desc"]).strip().endswith(label) and n["click"]]
    if not hit: raise SystemExit(f"no {label!r} on screen")
    x, y = ui.centre(hit[0]["bounds"]); ui.adb("shell", "input", "tap", str(x), str(y)); time.sleep(1.8)

def add(kind, amount, category, note):
    if not visible("Importo"):
        ui.main(["tap", "Aggiungi"]); time.sleep(2.5)
    ui.main(["tapexact", kind]); time.sleep(1.2)
    ui.adb("shell", "input", "tap", "600", "560"); time.sleep(1)       # the amount field
    ui.main(["keys", amount]); ui.main(["tapexact", "Fatto"]); time.sleep(2)
    tap_ends(category)
    ui.main(["tapexact", "Conti"]); time.sleep(1.8)
    ui.main(["type", note])
    ui.main(["tapexact", "Salva"]); time.sleep(2.5)

if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    which = sys.argv[1] if len(sys.argv) > 1 else "all"
    if which == "first":
        ui.main(["type", "Paycheck"]); ui.main(["tapexact", "Salva"]); time.sleep(2.5)
    elif which == "categories":
        ui.main(["tap", "Aggiungi"]); time.sleep(2.5); ui.main(["tapexact", "Spesa"]); time.sleep(1)
        ui.adb("shell", "input", "tap", "600", "560"); time.sleep(1); ui.main(["keys", "1"]); ui.main(["tapexact", "Fatto"]); time.sleep(2)
        ui.main(["texts"])
    else:
        for args in [a.split("|") for a in sys.argv[2:]]:
            add(*args)
        ui.main(["texts"])
