"""Print the labelled nodes of a saved dump: python tools/phone/texts_of.py <name>"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import ui
sys.stdout.reconfigure(encoding="utf-8")
xml = open(os.path.join(ui.ROOT, sys.argv[1] + ".xml"), encoding="utf-8").read()
for n in ui.nodes(xml):
    t = (n["text"] or n["desc"]).replace(chr(10), " / ")
    if t: print(ui.centre(n["bounds"]), "click" if n["click"] else "     ", t[:80])
