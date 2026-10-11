#!/usr/bin/env python3
"""Self-test for the gates (KICKOFF 7.3: a tool that answers a question must be tested on the
case where the thing it guards is already dead). Stdlib only.

  PASS gate_selftest: N assertions
Exit 1 on any failure.
"""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def run(cmd: list[str]) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, capture_output=True, text=True, cwd=ROOT, timeout=120)


def test_gate_detects_retired_prefix() -> tuple[bool, str]:
    """The known-truths gate must FAIL (verdict) on an asset containing 044078 - the exact
    failure the brief recorded (10.7, 10.8). It must NOT exit non-zero (never reddens builds)."""
    with tempfile.TemporaryDirectory() as td:
        asset = Path(td) / "asset.json"
        asset.write_text(json.dumps({"schema": 1, "trains": [
            {"number": "044078", "name": "retired-prefix poison", "from": "A", "to": "B"},
        ]}), encoding="utf-8")
        out = Path(td) / "verdict.json"
        proc = run([sys.executable, "scripts/known_truths_gate.py",
                    "--asset", str(asset), "--out", str(out)])
        if proc.returncode != 0:
            return False, f"gate exited {proc.returncode} (must be 0 even on data contradiction)"
        verdict = json.loads(out.read_text(encoding="utf-8"))
        if verdict["verdict"] != "FAIL":
            return False, f"verdict={verdict['verdict']} expected FAIL on retired prefix"
        if not verdict["phantom"]:
            return False, "phantom list empty; gate did not name the retired number"
    return True, "gate records FAIL (not exit) and names the retired number"


def test_gate_passes_on_clean_asset() -> tuple[bool, str]:
    with tempfile.TemporaryDirectory() as td:
        asset = Path(td) / "asset.json"
        asset.write_text(json.dumps({"schema": 1, "trains": [
            {"number": "16228", "name": "x", "from": "A", "to": "B"}]}), encoding="utf-8")
        out = Path(td) / "verdict.json"
        proc = run([sys.executable, "scripts/known_truths_gate.py",
                    "--asset", str(asset), "--out", str(out)])
        if proc.returncode != 0:
            return False, f"gate exited {proc.returncode}"
        verdict = json.loads(out.read_text(encoding="utf-8"))
        if verdict["verdict"] != "PASS":
            return False, f"verdict={verdict['verdict']} expected PASS on clean asset"
    return True, "gate PASSes a clean asset"


def test_docs_gate_fails_on_broken_status() -> tuple[bool, str]:
    """The docs gate must FAIL when STATUS.md's state vocabulary is broken (it asks the
    question)."""
    with tempfile.TemporaryDirectory() as td:
        bad = Path(td) / "STATUS.md"
        bad.write_text("| id | task | state | verified-by | next action | needs-device | owner-action |\n"
                       "|---|---|---|---|---|---|---|\n"
                       "| X-1 | t | WHATEVER | - | - | no | none |\n", encoding="utf-8")
        proc = subprocess.run(
            [sys.executable, "-c",
             "import sys; sys.path.insert(0, 'scripts'); import check_docs; "
             f"check_docs.ROOT = __import__('pathlib').Path(r'{td}'); sys.exit(check_docs.main())"],
            capture_output=True, text=True, cwd=ROOT, timeout=60)
        if proc.returncode == 0:
            return False, "docs gate passed a broken status table"
    return True, "docs gate fails loudly on a broken state vocabulary"


def main() -> int:
    tests = [test_gate_detects_retired_prefix, test_gate_passes_on_clean_asset,
             test_docs_gate_fails_on_broken_status]
    failures = 0
    for t in tests:
        try:
            ok, why = t()
        except Exception as e:
            ok, why = False, f"crashed: {e}"
        print(f"  [{'PASS' if ok else 'FAIL'}] {t.__name__}: {why}")
        failures += 0 if ok else 1
    if failures:
        print(f"FAIL gate_selftest: {failures} failing assertion(s)")
        return 1
    print(f"PASS gate_selftest: {len(tests)} assertions")
    return 0


if __name__ == "__main__":
    sys.exit(main())
