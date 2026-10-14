#!/usr/bin/env python3
r"""speccheck -- keeps scifinspec.md honest against itself and against the code it claims.

Harvested 2026-09-14 from forgetmenot's `tools/spec.mjs` (§14). Five hard checks and two soft:

  1. Every `§N` / `§N.M` reference resolves to a heading that exists.        (FAULT)
  2. Every backticked `name()` -- a claim that something is built -- exists as a
     symbol in sci-fin-prototype.html or under native/.                       (FAULT)
     A name found only in the prototype is reported separately: it exists, but
     not in the native app.                                                   (WARN)
  3. Dates are written in the file's escaped form `2026\-09\-14`; a bare
     `2026-09-14` is a convention break.                                      (FAULT)
  4. No two headings carry the same section number.                           (FAULT)
  5. Other backticked identifiers (camelCase / CONST_CASE) that appear nowhere
     in the code.                                                             (WARN only)
  6. A reference into the research audit ("audit §2.1, §3.3", "audit §4.1–§4.2")
     resolves to a heading of sci-fin-research-audit.md -- every ref of the run,
     not only the first.                                                      (FAULT)
  7. Counted claims are recounted: "**N `N.dp` literals remain under `<dir>`** (... in F
     files ...)" must match the tree today.                                   (FAULT)
  8. Every unstruck `File.kt:N` citation names a file under native/ that has a line N.
                                                                              (FAULT)
Both added 2026-09-25: the audit found the spec's counts right only because someone had
recounted by hand, and a citation to a line that had since been deleted.

The native corpus is source only: build/ and .gradle/ output is skipped, so a symbol that
survives only in a stale build artefact does not count as built (added 2026-09-25).

What it does NOT prove, stated so nobody reads green as more than it is: that a section's
prose describes what the code does. A symbol existing is not a mechanism matching. That
judgement stays with the person; this tool only removes the two silent failures that were
being checked by hand after every pass -- a reference to a section that no longer exists,
and a "built" claim about a function nobody wrote.

Exit 0 clean, 1 on any FAULT, 2 if the inputs cannot be read.
"""
import argparse
import io
import os
import re
import sys

# Platform APIs the spec names with `()` that are, correctly, in no file of ours: they are
# cited as evidence about the platform, not as claims that we built them.
PLATFORM_API = {"RequestAccessAsync"}

# Third-party documents the spec cites with the same § notation. A reference preceded by
# one of these words points into that document, not into this one.
EXTERNAL_DOC_WORDS = ("audit",)
AUDIT_FILE = "sci-fin-research-audit.md"
# What may sit between two refs of one run: "audit §2.1, §3.3", "§3.2/§4.1", "§4.1–§4.2".
RUN_JOIN_RE = re.compile(r"^\s*(?:,|/|–|-|and)\s*$")
SKIP_DIRS = {"build", ".gradle"}

HEADING_RE = re.compile(r"^(#{2,3})\s+(\d+)(?:\\?\.(\d+))?\\?\.?\s")
REF_RE = re.compile(r"§(\d+)(?:\\?\.(\d+))?")
BUILT_RE = re.compile(r"`([A-Za-z_]\w*)\(\)`")
IDENT_RE = re.compile(r"`([a-z][a-zA-Z0-9]*[A-Z][a-zA-Z0-9]*|[A-Z][A-Z0-9_]{3,})`")
BARE_DATE_RE = re.compile(r"(?<![\\\w])20\d\d-\d\d-\d\d\b")
CODE_SPAN_RE = re.compile(r"`[^`\n]*`")


def _utf8_stdout():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def code_corpus(root):
    """Everything a `name()` claim may be satisfied by, as (native source, prototype)."""
    native_parts = []
    native = os.path.join(root, "native")
    for dirpath, dirs, files in os.walk(native):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for fn in files:
            if fn.endswith((".kt", ".kts", ".xml", ".md")):
                native_parts.append(read(os.path.join(dirpath, fn)))
    proto = os.path.join(root, "sci-fin-prototype.html")
    return "\n".join(native_parts), (read(proto) if os.path.exists(proto) else "")


def heading_keys(text):
    keys = set()
    for line in text.split("\n"):
        m = HEADING_RE.match(line)
        if m:
            keys.add(m.group(2) + ("." + m.group(3) if m.group(3) else ""))
    return keys


def _has(name, text):
    return bool(re.search(r"\b" + re.escape(name) + r"\b", text))


def check(spec_text, corpus, audit_text=None):
    """corpus is (native source, prototype); audit_text None skips check 6."""
    native, proto = corpus
    audit_keys = heading_keys(audit_text) if audit_text is not None else None
    faults, warns = [], []
    lines = spec_text.split("\n")

    # ---- headings -------------------------------------------------------------------
    headings = {}
    for i, line in enumerate(lines, 1):
        m = HEADING_RE.match(line)
        if not m:
            continue
        key = m.group(2) + ("." + m.group(3) if m.group(3) else "")
        if key in headings:
            faults.append((i, f"duplicate heading §{key} (first at line {headings[key]})"))
        else:
            headings[key] = i
    if not headings:
        faults.append((0, "no numbered headings found -- is this the spec?"))

    # ---- § references ---------------------------------------------------------------
    n_refs = 0
    for i, line in enumerate(lines, 1):
        prev_end, in_audit = None, False
        for m in REF_RE.finditer(line):
            before = line[:m.start()].rstrip().lower()
            key = m.group(1) + ("." + m.group(2) if m.group(2) else "")
            joined = prev_end is not None and RUN_JOIN_RE.match(line[prev_end:m.start()])
            in_audit = any(before.endswith(w) for w in EXTERNAL_DOC_WORDS) or (in_audit and bool(joined))
            prev_end = m.end()
            if in_audit:
                if audit_keys is not None and key not in audit_keys:
                    faults.append((i, f"dangling audit reference §{key}"))
                continue
            n_refs += 1
            if key not in headings:
                faults.append((i, f"dangling reference §{key}"))

    # ---- built claims and identifiers ------------------------------------------------
    n_syms = 0
    seen = set()
    for i, line in enumerate(lines, 1):
        for m in BUILT_RE.finditer(line):
            name = m.group(1)
            n_syms += 1
            if name in PLATFORM_API or _has(name, native):
                continue
            if _has(name, proto):
                warns.append((i, f"built claim `{name}()` exists only in the prototype, not in native/"))
            else:
                faults.append((i, f"built claim `{name}()` names a symbol that exists in no file"))
        for m in IDENT_RE.finditer(line):
            name = m.group(1)
            if name in seen or name in PLATFORM_API:
                continue
            seen.add(name)
            if not _has(name, native) and not _has(name, proto):
                warns.append((i, f"`{name}` appears in no file (prose-only identifier?)"))

    # ---- date convention -------------------------------------------------------------
    for i, line in enumerate(lines, 1):
        stripped = CODE_SPAN_RE.sub("", line)
        for m in BARE_DATE_RE.finditer(stripped):
            faults.append((i, f"bare date {m.group(0)} -- write it as "
                              f"{m.group(0).replace('-', chr(92) + '-')}"))

    return faults, warns, {"headings": len(headings), "refs": n_refs, "built": n_syms}


DP_CLAIM_RE = re.compile(r"\*\*(\d+) `N\.dp` literals remain under `([^`]+)`\*\*[^\n]*?in (\d+) files")
CITE_RE = re.compile(r"`((?:[\w.-]+/)*[\w.-]+\.kt):(\d+)`")
STRUCK_RE = re.compile(r"~~.*?~~")


def check_tree(spec_text, root):
    """Checks 7 and 8: claims about the tree, recounted against it."""
    faults = []
    kt = []
    for dirpath, dirs, files in os.walk(os.path.join(root, "native")):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        kt += [os.path.join(dirpath, f) for f in files if f.endswith(".kt")]
    norm = [p.replace("\\", "/") for p in kt]
    for i, line in enumerate(spec_text.split("\n"), 1):
        live = STRUCK_RE.sub("", line)
        # a claim the pattern cannot read is a claim nobody checks: on 2026-10-07 "in ~~13~~ 11 files"
        # read as "in  11 files" once the strike was removed, matched nothing, and check 7 went silent
        if "`N.dp` literals remain under" in live and not DP_CLAIM_RE.search(live):
            faults.append((i, "a `N.dp` count claim this check cannot read -- keep \"**N `N.dp` literals remain under `dir`** ... in F files\" plain"))
        for m in DP_CLAIM_RE.finditer(live):
            want_n, sub, want_f = int(m.group(1)), m.group(2).strip("/"), int(m.group(3))
            n = files_with = 0
            for p, q in zip(kt, norm):
                if "/" + sub + "/" in q or q.startswith(sub + "/"):
                    c = len(re.findall(r"[0-9]\.dp\b", read(p)))
                    n += c; files_with += c > 0
            if (n, files_with) != (want_n, want_f):
                faults.append((i, f"the spec says {want_n} `N.dp` literals in {want_f} files under {sub}; the tree has {n} in {files_with}"))
        for m in CITE_RE.finditer(live):
            path, ln = m.group(1), int(m.group(2))
            hits = [p for p, q in zip(kt, norm) if q.endswith("/" + path)]
            if not hits:
                faults.append((i, f"citation `{path}:{ln}` names no file under native/"))
            elif all(sum(1 for _ in io.open(h, encoding="utf-8")) < ln for h in hits):
                faults.append((i, f"citation `{path}:{ln}` points past the end of the file"))
    return faults


def main(argv=None):
    _utf8_stdout()
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--root", default=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    ap.add_argument("--spec", default=None, help="defaults to <root>/scifinspec.md")
    ap.add_argument("--quiet-warnings", action="store_true")
    a = ap.parse_args(argv)
    spec_path = a.spec or os.path.join(a.root, "scifinspec.md")
    audit_path = os.path.join(a.root, AUDIT_FILE)
    try:
        spec = read(spec_path)
        corpus = code_corpus(a.root)
        audit = read(audit_path) if os.path.exists(audit_path) else None
    except OSError as e:
        print(f"cannot read inputs: {e}")
        return 2
    if not corpus[0]:
        print("no code found under root -- nothing for built claims to resolve against")
        return 2

    faults, warns, stats = check(spec, corpus, audit)
    faults += check_tree(spec, a.root)
    if audit is None:
        print(f"note: {AUDIT_FILE} not found -- audit references were not resolved")
    for ln, msg in faults:
        print(f"FAULT line {ln}: {msg}")
    if not a.quiet_warnings:
        for ln, msg in warns:
            print(f"WARN  line {ln}: {msg}")
    print(f"speccheck: {stats['headings']} headings, {stats['refs']} § references, "
          f"{stats['built']} built claims -- {len(faults)} fault(s), {len(warns)} warning(s)")
    return 1 if faults else 0


if __name__ == "__main__":
    sys.exit(main())
