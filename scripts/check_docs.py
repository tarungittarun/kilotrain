#!/usr/bin/env python3
"""Docs-freshness gate (KICKOFF 11.1/13.3). Stdlib only.

Checks:
  1. every file required by 13.1 exists;
  2. STATUS.md is ONE parseable table with the 13.3 state vocabulary;
  3. at most one owner-action row is open (answerable in one word).

Exit 0 = pass, 1 = fail naming the exact missing item.
This gate does not diff git history (docs-only rounds are legitimate); behaviour-changing
commits are bound by 13.3 and the story/STATUS row that names them.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

REQUIRED = [
    "docs/KICKOFF.md", "docs/PLAN.md", "docs/STATUS.md", "docs/COMPONENTS.md",
    "docs/LEARNINGS.md", "docs/DATA_SOURCES.md", "docs/COMPETITORS.md", "docs/QA_MATRIX.md",
    "docs/TRACEABILITY.md", "docs/RISK.md", "docs/CHANGELOG.md", "docs/RELEASES.md",
    "docs/RUNBOOK.md", "docs/TEST_CARD.md", "docs/stories", "docs/adr", "docs/reports",
    "poc/probe_sources.py",
]

STATE_RE = re.compile(r"^(NEXT|DOING|BLOCKED:[^|]+|HELD:[^|]+|DEFERRED:[^|]+|DONE:\d{4}-\d{2}-\d{2})$")


def main() -> int:
    missing = [p for p in REQUIRED if not (ROOT / p).exists()]
    if missing:
        print("FAIL docs-freshness: missing required files:")
        for p in missing:
            print(f"  - {p}")
        return 1

    status = (ROOT / "docs/STATUS.md").read_text(encoding="utf-8")
    # the task table is the FIRST table; later tables (metrics etc.) are not 13.3 rows
    task_section = status.split("## Metrics")[0]
    rows = [ln for ln in task_section.splitlines()
            if ln.startswith("|") and "---" not in ln and "| task |" not in ln]
    bad_rows = []
    open_owner_actions = 0
    for ln in rows:
        cells = [c.strip() for c in ln.strip().strip("|").split("|")]
        if len(cells) != 7:
            bad_rows.append(f"expected 7 cells, got {len(cells)}: {ln[:80]}")
            continue
        _id, _task, state, *_rest = cells
        owner_action = cells[6]
        if not STATE_RE.match(state):
            bad_rows.append(f"bad state {state!r} on row {_id}")
        if owner_action and owner_action.lower() != "none":
            open_owner_actions += 1
    if bad_rows:
        print("FAIL docs-freshness: STATUS.md rows:")
        for b in bad_rows:
            print(f"  - {b}")
        return 1
    if open_owner_actions > 1:
        print(f"FAIL docs-freshness: {open_owner_actions} open owner-action rows; at most ONE "
              "is allowed (13.3)")
        return 1
    print(f"PASS docs-freshness: {len(rows)} rows parsed, "
          f"{open_owner_actions} open owner-action row(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
