#!/usr/bin/env python3
"""Offline asset builder (KICKOFF 12.4). Stdlib only.

Today NO source is admitted (S1: no capture, ADR 0002; S2: throttle-only payloads; S3: labelled
fallback only; S4: unadmitted, ADR 0003), so the builder emits an HONEST EMPTY asset: schema
version + generated stamp + "source_admitted: none". The app must show unverified/empty states
rather than fabricate rows (3.4). The known-truths gate runs against whatever this emits.

Usage: python3 scripts/build_offline_asset.py [--out data/asset.json]
"""

from __future__ import annotations

import argparse
import datetime as _dt
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

ASSET_SCHEMA_VERSION = 1
ADMITTED_SOURCES: list[str] = []  # promote only via ADR (7.5); currently none


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(ROOT / "data/asset.json"))
    args = ap.parse_args()

    asset = {
        "schema": ASSET_SCHEMA_VERSION,
        "generated": _dt.datetime.now(_dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        # a BUILD stamp is not a data vintage (10.8): say so on the artefact itself
        "generated_note": "build timestamp, NOT a data vintage",
        "source_admitted": ADMITTED_SOURCES,
        "data_vintage": None,
        "trains": [],
        "honest_note": ("no source admitted; the app must render unverified/empty states, "
                        "never fabricated rows"),
    }
    Path(args.out).write_text(json.dumps(asset, indent=2), encoding="utf-8")
    print(f"asset written: {args.out} schema={ASSET_SCHEMA_VERSION} "
          f"admitted_sources={ADMITTED_SOURCES} trains=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
