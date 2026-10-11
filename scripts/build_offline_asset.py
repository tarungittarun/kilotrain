#!/usr/bin/env python3
"""Offline asset builder v2 (KICKOFF 12.4 + ADR 0007: S3 admitted as LABELLED cold-start fallback).

Pinned input + transform, no runtime scraping (3.5: the fetch happens here, in CI, once per
build - never from the app):
  * fetch trains.json at a PINNED commit SHA (rollback = regenerate with the pinned input),
  * DROP rows whose number uses the retired 0-prefix (pre-2022-23-renumbering content: wrong
    identity, and identity is never cleaned up - 3.4),
  * DROP geometry and unused fields (6.7: <= 6 MB gzipped),
  * KEEP number/name/from/to/departure/arrival/type/zone/classes, all labelled `timetable`
    with the dataset's measured vintage on the artefact (a build stamp is NOT a data vintage).

Usage:
  python3 scripts/build_offline_asset.py [--out FILE] [--from-file FILE] [--commit SHA]
"""

from __future__ import annotations

import argparse
import datetime as _dt
import json
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

ASSET_SCHEMA_VERSION = 2
# Pinned input (rollback = regenerate from this exact input; a bump is a deliberate change).
PINNED_COMMIT = "e0c538a1e41ae5eace454d2818902e1065608e16"
PINNED_URL = (
    f"https://raw.githubusercontent.com/datameet/railways/{PINNED_COMMIT}/trains.json"
)
# Measured vintage of the pinned input (NOT the build date - 10.8).
PINNED_VINTAGE = "2016-08-08 (repo commit date of the pin; CONTENT check caught 176 retired 0-prefix rows)"
PINNED_SOURCE = "datameet/railways@e0c538a1 (S3, cold-start fallback only, permanently labelled)"

RETIRED_PREFIX_RE = re.compile(r"^0\d{4,5}$")  # 06228/07301 (5-digit) and 044078 (6-digit): a leading 0 is the retired-prefix tell
VALID_NUMBER_RE = re.compile(r"^\d{5}$")
TIME_RE = re.compile(r"^\d{2}:\d{2}")

DROP_FIELDS = {"geometry"}


def transform(features: list[dict]) -> tuple[list[dict], dict]:
    trains, dropped = [], {"retired_prefix": 0, "incomplete": 0, "geometry_only": 0}
    for f in features:
        if "properties" not in f:
            dropped["geometry_only"] += 1
            continue
        p = f["properties"]
        number = str(p.get("number", "")).strip()
        name = str(p.get("name", "")).strip()
        from_c = str(p.get("from_station_code", "")).strip().upper()
        to_c = str(p.get("to_station_code", "")).strip().upper()
        departure = str(p.get("departure", "")).strip()
        arrival = str(p.get("arrival", "")).strip()
        if RETIRED_PREFIX_RE.match(number):
            dropped["retired_prefix"] += 1
            continue
        if not (VALID_NUMBER_RE.match(number) and name and from_c and to_c):
            dropped["incomplete"] += 1
            continue
        if not (TIME_RE.match(departure) and TIME_RE.match(arrival)):
            dropped["incomplete"] += 1
            continue
        trains.append({
            "number": number, "name": name,
            "from": from_c, "to": to_c,
            "departure": departure[:5], "arrival": arrival[:5],
            "type": str(p.get("type", "")).strip(),
            "zone": str(p.get("zone", "")).strip(),
        })
    trains.sort(key=lambda t: (t["from"], t["to"], t["departure"], t["number"]))
    return trains, dropped


def fetch() -> list[dict]:
    req = urllib.request.Request(PINNED_URL, headers={"User-Agent": "opentrack-asset-builder/2.0"})
    with urllib.request.urlopen(req, timeout=300) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    return payload.get("features", [])


def build(features: list[dict]) -> dict:
    trains, dropped = transform(features)
    return {
        "schema": ASSET_SCHEMA_VERSION,
        "generated": _dt.datetime.now(_dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "generated_note": "build timestamp, NOT a data vintage (10.8)",
        "source_admitted": ["S3 datameet/railways (labelled cold-start fallback)"],
        "source": PINNED_SOURCE,
        "data_vintage": PINNED_VINTAGE,
        "provenance_kind": "timetable",
        "dropped": dropped,
        "trains": trains,
        "honest_note": ("2016-vintage fallback identity data. Live sources (S1/S2) are blocked; "
                        "this data is NOT the railway's current timetable and the app must say so"),
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(ROOT / "data/asset.json"))
    ap.add_argument("--from-file", default=None,
                    help="build from a local file instead of fetching (tests, offline runs)")
    ap.add_argument("--commit", default=PINNED_COMMIT, help="override the pinned commit")
    args = ap.parse_args()

    if args.from_file:
        features = json.loads(Path(args.from_file).read_text(encoding="utf-8")).get("features", [])
    else:
        try:
            features = fetch()
        except Exception as e:
            print(f"ASSET-BUILD FAILED: cannot fetch the pinned input ({type(e).__name__}: {e}). "
                  "This is a stated failure, never a silent empty asset (3.4).", file=sys.stderr)
            return 2
    asset = build(features)
    Path(args.out).write_text(json.dumps(asset, ensure_ascii=False), encoding="utf-8")
    size = Path(args.out).stat().st_size
    print(f"asset written: {args.out} schema={ASSET_SCHEMA_VERSION} trains={len(asset['trains'])} "
          f"dropped={asset['dropped']} bytes={size} (budget 6 MB gzipped - 6.7)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
