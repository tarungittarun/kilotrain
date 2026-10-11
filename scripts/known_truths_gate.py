#!/usr/bin/env python3
"""Known-truths gate (KICKOFF 10.7). Stdlib only.

Reads data/known_truths.json (OUTSIDE the codebase: a handful of human-verified, dated,
attributed facts - NO minutes, which legitimately move) and an offline asset JSON).

Rules:
  * an assertion with status != "verified" is NOT asserted (suspected is not asserted);
  * the gate reports MISSING claims (fact absent from asset) and PHANTOM claims (asset has
    something the fixture contradicts);
  * the gate NEVER turns the build red on a data contradiction (10.7): it records the verdict
    so the artefact can state it and the UI can honour it;
  * exit 0 = gate ran (verdict may still be FAIL - that is information, not a build break);
    exit 2 only if the gate itself could not run.

Usage: python3 scripts/known_truths_gate.py [--asset FILE] [--truths FILE] [--out FILE]
"""

from __future__ import annotations

import argparse
import datetime as _dt
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# The pattern the 2022-23 renumbering retired: 5-digit numbers led by 0. Observed live on
# 2026-10-11 in datameet/railways/trains.json head (3 hits, 044078) - proof that a repo commit
# date is not a data vintage.
RETIRED_PREFIX_RE = re.compile(r"\b0\d{5}\b")

FALLBACK: dict = {
    "schema": "known-truths/1",
    "claims": [],
    "identity_claims": [
        {
            "kind": "train-identity",
            "train": "16228",
            "expect": {"name_contains": ""},
            "evidence": "pending owner verification (paste from the official enquiry app)",
            "status": "pending-evidence",
        },
    ],
}


def load_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        return {}
    except json.JSONDecodeError as e:
        print(f"GATE-ERROR: {path} is not valid JSON: {e}", file=sys.stderr)
        raise SystemExit(2)


def run(asset: dict, truths: dict) -> dict:
    rows = []
    missing, phantom, passed = [], [], []

    # Assertion 1 (always on, evidence-backed): no retired 0-prefix numbers anywhere in the asset.
    blob = json.dumps(asset, ensure_ascii=False)
    retired = sorted(set(RETIRED_PREFIX_RE.findall(blob)))
    rows.append({
        "assertion": "no retired 0-prefix train numbers in asset",
        "status": "PASS" if not retired else "FAIL",
        "evidence": "2022-23 renumbering; observed 044078 in datameet/railways head 2026-10-11",
        "detail": {"retired_found": retired[:10]},
    })
    if retired:
        phantom.append({"kind": "retired-prefix", "values": retired[:10]})

    # Assertion 2: every VERIFIED train-identity/pair-presence claim must be honoured;
    # unverified claims are not asserted (suspected is not asserted, 10.7).
    asset_trains = asset.get("trains", []) or []

    def train_in_asset(number: str) -> bool:
        if isinstance(asset_trains, dict):
            return number in asset_trains
        return any(str(t.get("number", "")) == number for t in asset_trains)

    for claim in truths.get("claims", []) + truths.get("identity_claims", []):
        if claim.get("status") != "verified":
            rows.append({"assertion": f"skip(unverified) {claim.get('kind')} {claim.get('train', '')}",
                         "status": "SKIPPED-NOT-ASSERTED", "evidence": claim.get("evidence", "")})
            continue
        if claim.get("kind") not in ("train-identity", "pair-presence"):
            continue  # pattern claims are asserted by assertion 1, not here
        train = str(claim.get("train", ""))
        expect = claim.get("expect", {})
        found = train_in_asset(train)
        if not found:
            missing.append({"kind": claim.get("kind"), "train": train})
            rows.append({"assertion": f"claim present {train}", "status": "FAIL",
                         "evidence": claim.get("evidence", "")})
        else:
            name_contains = expect.get("name_contains", "")
            if name_contains and name_contains not in json.dumps(asset, ensure_ascii=False):
                phantom.append({"kind": "name-mismatch", "train": train, "expect": name_contains})
                rows.append({"assertion": f"claim name {train}", "status": "FAIL",
                             "evidence": claim.get("evidence", "")})
            else:
                passed.append(train)
                rows.append({"assertion": f"claim {train}", "status": "PASS",
                             "evidence": claim.get("evidence", "")})

    verdict_fail = bool(missing or phantom)
    return {
        "run": _dt.datetime.now(_dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "verdict": "FAIL" if verdict_fail else ("PASS" if rows else "EMPTY-NO-ASSERTIONS"),
        "build_impact": "NONE (10.7: a data contradiction records a verdict, it never reddens the build)",
        "missing": missing,
        "phantom": phantom,
        "passed_claims": passed,
        "rows": rows,
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--asset", default=str(ROOT / "data/asset.json"))
    ap.add_argument("--truths", default=str(ROOT / "data/known_truths.json"))
    ap.add_argument("--out", default=str(ROOT / "data/gate-verdict.json"))
    args = ap.parse_args()

    asset = load_json(Path(args.asset))
    truths = load_json(Path(args.truths)) or FALLBACK
    result = run(asset, truths)
    Path(args.out).write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(f"known-truths gate {result['run']}: verdict={result['verdict']} "
          f"(build impact: {result['build_impact']})")
    for r in result["rows"]:
        print(f"  [{r['status']}] {r['assertion']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
