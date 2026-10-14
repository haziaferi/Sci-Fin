#!/usr/bin/env python3
r"""selftest -- proves speccheck can fail.

A checker that has only ever printed green against the real tree is not known to work
(forgetmenot §8, harvested 2026-09-14). This builds a fixture from the real spec and code in
a temp directory, confirms speccheck passes on it, then applies one known fault at a time and
requires exit 1 *and* the fault named in the output. One case is a negative: a struck-through
reference to a real section must still pass, because struck text is the decision trail and
its references are checked like any other. Each fault starts from a fresh copy, so a mutation
cannot leak into the next case; a fault that leaves the file byte-identical is a broken test,
not a pass.

Exit 0 when every case behaves, 1 otherwise.
"""
import io
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
CHECK = os.path.join(HERE, "speccheck.py")


def _utf8_stdout():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def build_fixture(dst):
    shutil.copy(os.path.join(ROOT, "scifinspec.md"), dst)
    shutil.copy(os.path.join(ROOT, "sci-fin-prototype.html"), dst)
    shutil.copy(os.path.join(ROOT, "sci-fin-research-audit.md"), dst)
    # source only: build/ and .gradle/ are ~400 MB that speccheck skips anyway
    shutil.copytree(os.path.join(ROOT, "native"), os.path.join(dst, "native"),
                    ignore=shutil.ignore_patterns("build", ".gradle"))


def run(root, warnings=False):
    args = [sys.executable, CHECK, "--root", root] + ([] if warnings else ["--quiet-warnings"])
    p = subprocess.run(args, capture_output=True, text=True, encoding="utf-8")
    return p.returncode, p.stdout + p.stderr


# ---- the fault table -----------------------------------------------------------------
# (name, mutate(spec_text) -> spec_text, expected exit, substring the output must contain)

def dangling_ref(s):
    return s.replace("## 14\\. Document Notes", "## 14\\. Document Notes\n\nSee §99.7 for nothing.", 1)


def phantom_built(s):
    return s.replace("## 14\\. Document Notes", "## 14\\. Document Notes\n\n`neverWritten()` does it.", 1)


def bare_date(s):
    return s.replace("## 14\\. Document Notes", "## 14\\. Document Notes\n\nDecided 2026-09-14.", 1)


def duplicate_heading(s):
    return s.replace("## 14\\. Document Notes", "### 13\\.4 Again\n\n## 14\\. Document Notes", 1)


def struck_real_ref(s):  # negative: must pass
    return s.replace("## 14\\. Document Notes", "## 14\\. Document Notes\n\n~~was §3.6~~ now §3.6.", 1)


def escaped_date_ok(s):  # negative: must pass
    return s.replace("## 14\\. Document Notes", "## 14\\. Document Notes\n\nDecided 2026\\-09\\-14.", 1)


def external_doc_ref_ok(s):  # negative: "audit §2.1" points into another document
    return s.replace("## 14\\. Document Notes", "## 14\\. Document Notes\n\nSee audit §2.1.", 1)


def audit_run_dangling(s):  # the second ref of an audit run is resolved against the audit
    return s.replace("## 14\\. Document Notes", "## 14\\. Document Notes\n\nSee audit §2.1, §9.9.", 1)


def audit_run_ok(s):  # negative: every ref of the run exists in the audit
    return s.replace("## 14\\. Document Notes", "## 14\\. Document Notes\n\nSee audit §2.1, §3.3/§4.1–§4.2.", 1)


def prototype_only_built(s):  # a symbol only the prototype has is a warning, not a silent pass
    return s.replace("## 14\\. Document Notes", "## 14\\. Document Notes\n\n`monthlyEquivalent()` does it.", 1)


def dp_claim_wrong(s):  # a counted claim that no longer matches the tree
    # one below whatever the spec states now: a fixed "62" went byte-identical when the count moved (2026-09-26)
    return re.sub(r"\*\*(\d+) `N\.dp` literals remain under", lambda m: f"**{int(m.group(1)) - 1} `N.dp` literals remain under", s, count=1)


def dp_claim_unreadable(s):  # the claim written so check 7's pattern cannot read it (2026-10-07: a struck file count)
    return re.sub(r"(`N\.dp` literals remain under[^\n]*?)in (\d+) files", lambda m: f"{m.group(1)}in ~~{int(m.group(2)) + 2}~~ {m.group(2)} files", s, count=1)


def citation_past_end(s):  # a file:line citation to a line that is not there
    return s.replace("## 14\\. Document Notes", "## 14\\. Document Notes\n\nSee `ui/Charts.kt:99999`.", 1)


CASES = [
    ("dangling § reference",          dangling_ref,         1, "dangling reference §99.7"),
    ("built claim with no symbol",    phantom_built,        1, "`neverWritten()`"),
    ("bare-hyphen date",              bare_date,            1, "bare date 2026-09-14"),
    ("duplicate heading number",      duplicate_heading,    1, "duplicate heading §13.4"),
    ("struck ref to real section",    struck_real_ref,      0, "0 fault(s)"),
    ("escaped date",                  escaped_date_ok,      0, "0 fault(s)"),
    ("external-document § ref",       external_doc_ref_ok,  0, "0 fault(s)"),
    ("audit run, second ref missing", audit_run_dangling,   1, "dangling audit reference §9.9"),
    ("audit run, all refs real",      audit_run_ok,         0, "0 fault(s)"),
    ("prototype-only built claim",    prototype_only_built, 0, "exists only in the prototype"),
    ("counted claim out of date",     dp_claim_wrong,       1, "`N.dp` literals in"),
    ("counted claim unreadable",      dp_claim_unreadable,  1, "cannot read"),
    ("citation past end of file",     citation_past_end,    1, "points past the end of the file"),
]
# cases whose needle is a warning, so their run must print warnings
WARN_CASES = {"prototype-only built claim"}


def main():
    _utf8_stdout()
    bad = 0
    with tempfile.TemporaryDirectory() as tmp:
        clean = os.path.join(tmp, "clean")
        os.makedirs(clean)
        build_fixture(clean)
        rc, out = run(clean)
        status = "ok" if rc == 0 else "FAIL"
        if rc != 0:
            bad += 1
        print(f"[{status:4}] clean fixture passes                (exit {rc})")

        for name, mutate, want_rc, needle in CASES:
            d = os.path.join(tmp, name.replace(" ", "_").replace("§", "s").replace(",", ""))
            os.makedirs(d)
            build_fixture(d)
            spec_path = os.path.join(d, "scifinspec.md")
            before = io.open(spec_path, encoding="utf-8", newline="").read()
            after = mutate(before)
            if after == before:
                print(f"[FAIL] {name:36} mutation left the file byte-identical -- broken test")
                bad += 1
                continue
            io.open(spec_path, "w", encoding="utf-8", newline="").write(after)
            rc, out = run(d, warnings=name in WARN_CASES)
            ok = rc == want_rc and needle in out
            if not ok:
                bad += 1
            what = "named the fault" if want_rc or name in WARN_CASES else "reported clean"
            if needle not in out:
                what = "DID NOT name the fault" if want_rc or name in WARN_CASES else "DID NOT report clean"
            print(f"[{'ok' if ok else 'FAIL':4}] {name:36} exit {rc} (want {want_rc}), {what}")
            if not ok:
                print("       ---- output ----")
                print("       " + out.strip().replace("\n", "\n       "))

    print(f"selftest: {len(CASES) + 1} cases, {bad} misbehaving")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
