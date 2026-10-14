"""Cashew: the comparison's sample data (2026-10-07). Income 1450 already entered by hand."""
import os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
import ui

def add(title, kind, category, amount):
    ui.adb("shell", "input", "tap", "954", "1848"); time.sleep(2)          # the + button
    ui.main(["type", title])
    ui.main(["tap", "Seleziona categoria"]); time.sleep(1.5)
    ui.main(["tapexact", kind]); time.sleep(1)
    hit = [n for n in ui.nodes(ui.hierarchy()) if (n["text"] or n["desc"]).strip() == category]
    x, y = ui.centre(hit[0]["bounds"]); ui.adb("shell", "input", "tap", str(x), str(hit[0]["bounds"][1] - 110)); time.sleep(2)   # the icon above its label
    ui.main(["keys", amount])
    ui.main(["tap", "Aggiungi transazione"]); time.sleep(2)

def resume_income():                            # the first entry stopped at the category sheet
    hit = [n for n in ui.nodes(ui.hierarchy()) if (n["text"] or n["desc"]).strip() == "Reddito"]
    x, y = ui.centre(hit[0]["bounds"]); ui.adb("shell", "input", "tap", str(x), str(hit[0]["bounds"][1] - 110)); time.sleep(2)
    ui.main(["keys", "1617"]); ui.main(["tap", "Aggiungi transazione"]); time.sleep(2)

if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    resume_income()
    add("Groceries", "Spesa", "Alimentari", "86")
    add("Coffee", "Spesa", "Mangiare", "12")
    add("Utilities", "Spesa", "Bollette e tasse", "120")
    add("Train", "Spesa", "Trasporti", "35")
    ui.main(["texts"])
