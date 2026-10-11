#!/usr/bin/env python3
"""Asset-builder self-test (KICKOFF 7.3): the transform must ask its questions with a capture
in hand - here a small redacted sample (scripts/fixtures/s3_trains_sample.json), never the live
network. Stdlib only.

PASS asset_selftest: N assertions
"""

from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load_builder():
    spec = importlib.util.spec_from_file_location(
        "build_offline_asset", ROOT / "scripts/build_offline_asset.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def test_retired_prefix_is_dropped(b) -> tuple[bool, str]:
    features = json.loads((ROOT / "scripts/fixtures/s3_trains_sample.json").read_text())["features"]
    trains, dropped = b.transform(features)
    numbers = {t["number"] for t in trains}
    if "06228" in numbers:
        return False, "retired 0-prefix number 06228 survived the transform"
    if dropped["retired_prefix"] != 1:
        return False, f"dropped['retired_prefix']={dropped['retired_prefix']} expected 1"
    return True, "06228 dropped and counted"


def test_valid_rows_survive_with_times(b) -> tuple[bool, str]:
    features = json.loads((ROOT / "scripts/fixtures/s3_trains_sample.json").read_text())["features"]
    trains, _ = b.transform(features)
    row = next((t for t in trains if t["number"] == "56269"), None)
    if row is None:
        return False, "56269 missing"
    if row["from"] != "SMET" or row["to"] != "MYS":
        return False, f"endpoints wrong: {row}"
    if row["departure"] != "10:30" or row["arrival"] != "16:55":
        return False, f"times wrong: {row}"
    if "geometry" in row:
        return False, "geometry survived"
    return True, "56269 kept with HH:MM times and no geometry"


def test_short_number_is_dropped(b) -> tuple[bool, str]:
    features = json.loads((ROOT / "scripts/fixtures/s3_trains_sample.json").read_text())["features"]
    trains, dropped = b.transform(features)
    if any(t["number"] == "4444" for t in trains):
        return False, "4-digit number survived"
    if dropped["incomplete"] != 1:
        return False, f"dropped['incomplete']={dropped['incomplete']} expected 1"
    return True, "short number dropped as incomplete"


def test_built_asset_carries_vintage_and_schema(b) -> tuple[bool, str]:
    features = json.loads((ROOT / "scripts/fixtures/s3_trains_sample.json").read_text())["features"]
    asset = b.build(features)
    if asset["schema"] != 2:
        return False, f"schema={asset['schema']} expected 2"
    if "2016" not in asset["data_vintage"]:
        return False, "data vintage not stated on the artefact"
    if "generated_note" not in asset:
        return False, "build stamp not marked as NOT a vintage"
    return True, "schema 2, vintage stated, build stamp marked"


def test_smet_mys_count_after_filter(b) -> tuple[bool, str]:
    features = json.loads((ROOT / "scripts/fixtures/s3_trains_sample.json").read_text())["features"]
    trains, _ = b.transform(features)
    smet_mys = [t for t in trains if t["from"] == "SMET" and t["to"] == "MYS"]
    if len(smet_mys) != 1:
        return False, f"SMET->MYS rows after filter: {len(smet_mys)} expected 1"
    return True, "exactly one honest SMET->MYS row remains"


def main() -> int:
    b = load_builder()
    tests = [test_retired_prefix_is_dropped, test_valid_rows_survive_with_times,
             test_short_number_is_dropped, test_built_asset_carries_vintage_and_schema,
             test_smet_mys_count_after_filter]
    failures = 0
    for t in tests:
        try:
            ok, why = t(b)
        except Exception as e:
            ok, why = False, f"crashed: {e}"
        print(f"  [{'PASS' if ok else 'FAIL'}] {t.__name__}: {why}")
        failures += 0 if ok else 1
    if failures:
        print(f"FAIL asset_selftest: {failures} failing assertion(s)")
        return 1
    print(f"PASS asset_selftest: {len(tests)} assertions")
    return 0


if __name__ == "__main__":
    sys.exit(main())
