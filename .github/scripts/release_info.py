#!/usr/bin/env python3
"""Validates a release tag and prints the module, version and release notes.

Usage: release_info.py <tag> <notes-output-file>
Tag format: <module>-v<version>, e.g. claims-v0.1.0 or claims-api-v1.2.3.
Writes GitHub Actions outputs (module, version) to $GITHUB_OUTPUT when it is set.
"""
import os
import re
import sys

MODULES = ["claims-api", "claims", "webmap", "clientmap"]


def fail(msg):
    print(f"::error::{msg}")
    sys.exit(1)


def main():
    tag, notes_path = sys.argv[1], sys.argv[2]
    m = re.fullmatch(r"(.+)-v(\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?)", tag)
    if not m or m.group(1) not in MODULES:
        fail(f"Tag '{tag}' is not <module>-v<semver> with module in {MODULES}")
    module, version = m.group(1), m.group(2)

    with open(os.path.join(module, "gradle.properties"), encoding="utf-8") as f:
        props = dict(line.strip().split("=", 1) for line in f if "=" in line and not line.startswith("#"))
    if props.get("mod_version") != version:
        fail(f"{module}/gradle.properties has mod_version={props.get('mod_version')}, tag says {version}")

    with open("CHANGELOG.md", encoding="utf-8") as f:
        text = f.read()
    section = re.search(rf"^## {re.escape(module)}\n(.*?)(?=^## |\Z)", text, re.S | re.M)
    if not section:
        fail(f"CHANGELOG.md has no '## {module}' section")
    entry = re.search(rf"^### \[{re.escape(version)}\][^\n]*\n(.*?)(?=^### |\Z)", section.group(1), re.S | re.M)
    if not entry or not entry.group(1).strip():
        fail(f"CHANGELOG.md has no entry for {module} {version}")

    with open(notes_path, "w", encoding="utf-8") as f:
        f.write(entry.group(1).strip() + "\n")
    out = os.environ.get("GITHUB_OUTPUT")
    if out:
        with open(out, "a", encoding="utf-8") as f:
            f.write(f"module={module}\nversion={version}\n")
    print(f"module={module} version={version}")


if __name__ == "__main__":
    main()
