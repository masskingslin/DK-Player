#!/usr/bin/env python3
"""Fail the build early if source files look truncated or corrupted.

Checks (run from the repository root: `python3 tools/check_sources.py`):
  * *.kt / *.java : brackets (), [], {} balance, ignoring comments, strings and chars.
  * *.xml         : parses as well-formed XML.
  * *.kts / *.gradle / *.json : brackets balance (json also parses).
Exit code 0 = all good, 1 = problems found, 2 = bad usage.
"""
import json
import os
import sys
import xml.etree.ElementTree as ET

SKIP_DIRS = {".git", ".gradle", "build", ".idea", "node_modules", ".github"}
PAIRS = {")": "(", "]": "[", "}": "{"}


def check_brackets(text):
    """Return an error string, or None if balanced."""
    stack = []  # (char, line)
    i, n, line = 0, len(text), 1
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if c == "\n":
            line += 1
        elif c == "/" and nxt == "/":                      # line comment
            while i < n and text[i] != "\n":
                i += 1
            continue
        elif c == "/" and nxt == "*":                      # block comment (nests in Kotlin)
            depth = 1
            i += 2
            while i < n and depth:
                if text.startswith("/*", i):
                    depth += 1
                    i += 2
                elif text.startswith("*/", i):
                    depth -= 1
                    i += 2
                else:
                    if text[i] == "\n":
                        line += 1
                    i += 1
            continue
        elif text.startswith('"""', i):                    # raw string
            j = text.find('"""', i + 3)
            if j < 0:
                return f"unterminated raw string starting line {line}"
            line += text.count("\n", i, j)
            i = j + 3
            while i < n and text[i] == '"':                # trailing extra quotes
                i += 1
            continue
        elif c == '"':                                     # normal string
            i += 1
            while i < n and text[i] != '"':
                if text[i] == "\\":
                    i += 1
                elif text[i] == "\n":
                    return f"unterminated string literal on line {line}"
                elif text[i] == "$" and i + 1 < n and text[i + 1] == "{":
                    # skip a ${...} template, which may contain quotes
                    depth, i = 1, i + 2
                    while i < n and depth:
                        if text[i] == "{":
                            depth += 1
                        elif text[i] == "}":
                            depth -= 1
                        i += 1
                    continue
                i += 1
            i += 1
            continue
        elif c == "'":                                     # char literal
            j = i + 1
            if j < n and text[j] == "\\":
                j += 1
            j += 1
            if j < n and text[j] != "'":                   # e.g. "'s" inside odd text; ignore
                i += 1
                continue
            i = j + 1
            continue
        elif c in "([{":
            stack.append((c, line))
        elif c in ")]}":
            if not stack or stack[-1][0] != PAIRS[c]:
                return f"unexpected '{c}' on line {line}"
            stack.pop()
        i += 1
    if stack:
        c, l = stack[-1]
        return f"{len(stack)} unclosed bracket(s); last '{c}' opened on line {l} (file looks truncated)"
    return None


def check_file(path):
    ext = os.path.splitext(path)[1].lower()
    try:
        with open(path, encoding="utf-8-sig") as f:
            text = f.read()
    except (UnicodeDecodeError, OSError) as e:
        return f"cannot read as UTF-8: {e}"
    if "\x00" in text:
        return "contains NUL bytes (corrupted)"
    if ext in (".kt", ".java", ".kts", ".gradle"):
        return check_brackets(text)
    if ext == ".xml":
        try:
            ET.fromstring(text.encode("utf-8"))
        except ET.ParseError as e:
            return f"malformed XML: {e}"
    if ext == ".json":
        try:
            json.loads(text)
        except ValueError as e:
            return f"invalid JSON: {e}"
    return None


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    if not os.path.isdir(root):
        print(f"not a directory: {root}", file=sys.stderr)
        return 2
    exts = {".kt", ".java", ".kts", ".gradle", ".xml", ".json"}
    checked, bad = 0, []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in filenames:
            if os.path.splitext(name)[1].lower() in exts:
                path = os.path.join(dirpath, name)
                checked += 1
                err = check_file(path)
                if err:
                    bad.append((path, err))
    for path, err in bad:
        print(f"::error file={path}::{err}")
    print(f"Checked {checked} source files, {len(bad)} problem(s).")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
