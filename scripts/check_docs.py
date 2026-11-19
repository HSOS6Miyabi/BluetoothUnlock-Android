#!/usr/bin/env python3
"""Validate local Markdown links and Android app version consistency."""
from pathlib import Path
import re
import sys
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    "README.md", "docs/HANDOVER.md", "docs/ARCHITECTURE.md",
    "docs/PROJECT_LEDGER.md", "docs/DEVELOPMENT.md",
    "docs/DEVICE_TEST_RECORD.md", "docs/PROTOCOL.md",
    "docs/SECURITY.md", "docs/TESTING.md",
]
LINK = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
errors = []
for path in REQUIRED:
    if not (ROOT / path).is_file():
        errors.append(f"Required document is missing: {path}")
for doc in [ROOT / "README.md", *sorted((ROOT / "docs").glob("*.md"))]:
    text = doc.read_text(encoding="utf-8")
    for target in LINK.findall(text):
        target = target.strip().split(" ", 1)[0].strip("<>")
        if not target or target.startswith(("#", "https://", "http://", "mailto:")):
            continue
        relative = unquote(target.split("#", 1)[0])
        if relative and not (doc.parent / relative).exists():
            errors.append(f"{doc.relative_to(ROOT)}: missing link target {target}")
version = (ROOT / "VERSION").read_text(encoding="utf-8").strip()
if not re.fullmatch(r"\d+\.\d+\.\d+", version):
    errors.append("VERSION must contain a three-part numeric version")
build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
if f'versionName = "{version}"' not in build:
    errors.append("Gradle versionName and VERSION differ")
if not re.search(r"versionCode\s*=\s*[1-9]\d*", build):
    errors.append("Android versionCode must be a positive integer")
if errors:
    for item in errors:
        print(item, file=sys.stderr)
    sys.exit(1)
print(f"Documentation and version check passed: Android {version}")
