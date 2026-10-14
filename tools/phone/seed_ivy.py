"""Ivy Wallet: the comparison's sample data (2026-10-08). The first income is already at its title step."""
import os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
import ui

def finish(title):
    ui.main(["type", title]); time.sleep(0.5)
    ui.main(["tapexact", "Aggiungi"]); time.sleep(2.5)

def add(kind, amount, category, title):
    ui.main(["tap", "circle button"]); time.sleep(2)                  # the + on Home
    ui.main(["tap", kind]); time.sleep(2.5)
    ui.main(["keys", amount]); time.sleep(0.3)
    ui.adb("shell", "input", "tap", "860", "2090"); time.sleep(2.5)  # Invia
    if category: ui.main(["tapexact", category])
    else: ui.main(["tapexact", "Salta"])
    time.sleep(2)
    finish(title)

if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    finish("Paycheck")
    add("AGGIUNGI ENTRATA", "1617", None, "Starting balance")
    add("AGGIUNGI SPESA", "86", "Alimentari", "Groceries")
    add("AGGIUNGI SPESA", "12", "Cibo e bevande", "Coffee")
    add("AGGIUNGI SPESA", "120", "Bollette e tasse", "Utilities")
    add("AGGIUNGI SPESA", "35", "Trasporti", "Train")
    ui.main(["texts"])
