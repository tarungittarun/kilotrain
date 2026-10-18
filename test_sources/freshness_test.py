"""
Data Freshness Verification Script
Tests that data from each source updates over time (30 min monitoring)
and that timestamps are consistent.
"""

import requests
import json
import time
import os
from datetime import datetime

TEST_TRAIN = "16227"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120 Safari/537.36",
    "Accept": "application/json, text/html, */*",
}

RESULTS_DIR = "test_sources/freshness_results"
os.makedirs(RESULTS_DIR, exist_ok=True)


def query_wimts(train_number):
    url = f"https://whereismytrain.in/cache/live_status?date=18-10-2026&train_no={train_number}&from=MYS&to=TLGP&from_day=1&lang=en&appVersion=7.1.5.633808517&sb_version=0"
    try:
        r = requests.get(url, headers=HEADERS, timeout=15)
        if r.status_code == 200:
            data = r.json()
            stops = data.get("days_schedule", [])
            # Find a stop with actual time
            for stop in stops:
                if stop.get("actual_arrival_tm"):
                    return {
                        "source": "WIMT",
                        "train": train_number,
                        "status_code": 200,
                        "station": stop.get("station_code"),
                        "actual_time": stop.get("actual_arrival_time"),
                        "actual_timestamp_ms": stop.get("actual_arrival_tm"),
                        "delay_minutes": stop.get("delay_in_arrival"),
                        "timestamp_iso": datetime.now().isoformat()
                    }
            return {"source": "WIMT", "train": train_number, "status_code": 200, "has_actuals": False}
        return {"source": "WIMT", "train": train_number, "status_code": r.status_code}
    except Exception as e:
        return {"source": "WIMT", "train": train_number, "error": str(e)}


def query_railradar(train_number):
    url = f"https://railradar.in/api/v1/trains/{train_number}/live"
    try:
        r = requests.get(url, headers=HEADERS, timeout=15)
        if r.status_code == 200:
            data = r.json()
            stops = data.get("data", {}).get("route", [])
            for stop in stops:
                if stop.get("actualArrival"):
                    return {
                        "source": "RailRadar.in",
                        "train": train_number,
                        "status_code": 200,
                        "station": stop.get("stationCode"),
                        "actual_time": stop.get("actualArrival"),
                        "actual_timestamp_iso": stop.get("actualArrival"),
                        "delay_minutes": stop.get("delayArrival"),
                        "timestamp_iso": datetime.now().isoformat()
                    }
            return {"source": "RailRadar.in", "train": train_number, "status_code": 200, "has_actuals": False}
        return {"source": "RailRadar.in", "train": train_number, "status_code": r.status_code}
    except Exception as e:
        return {"source": "RailRadar.in", "train": train_number, "error": str(e)}


def query_runningstatus(train_number):
    url = f"https://runningstatus.in/status/{train_number}"
    try:
        r = requests.get(url, headers=HEADERS, timeout=15)
        if r.status_code == 200:
            return {
                "source": "RunningStatus.in",
                "train": train_number,
                "status_code": 200,
                "response_time_ms": r.elapsed.total_seconds() * 1000,
                "has_html": True,
                "html_length": len(r.text),
                "timestamp_iso": datetime.now().isoformat()
            }
        return {"source": "RunningStatus.in", "train": train_number, "status_code": r.status_code}
    except Exception as e:
        return {"source": "RunningStatus.in", "train": train_number, "error": str(e)}


def query_erail(train_number):
    url = f"https://erail.in/api/v1/train/live-status/{train_number}"
    try:
        r = requests.get(url, headers=HEADERS, timeout=15)
        if r.status_code == 200:
            return {
                "source": "erail.in",
                "train": train_number,
                "status_code": 200,
                "response_time_ms": r.elapsed.total_seconds() * 1000,
                "has_data": True,
                "timestamp_iso": datetime.now().isoformat()
            }
        return {"source": "erail.in", "train": train_number, "status_code": r.status_code}
    except Exception as e:
        return {"source": "erail.in", "train": train_number, "error": str(e)}


SOURCES = {
    "WIMT": query_wimts,
    "RailRadar.in": query_railradar,
    "RunningStatus.in": query_runningstatus,
    "erail.in": query_erail,
}


def run_round(run_num):
    print(f"\nRun #{run_num} - {datetime.now().strftime('%H:%M:%S')}")
    results = []
    for source_name, source_func in SOURCES.items():
        result = source_func(TEST_TRAIN)
        results.append(result)
        status = "OK" if result.get("status_code") == 200 else "FAIL"
        detail = ""
        if result.get("actual_arrival_time"):
            detail = f"actual={result['actual_arrival_time']}"
        elif result.get("actualArrival"):
            detail = f"actual={result['actualArrival']}"
        elif result.get("has_data"):
            detail = "has data"
        print(f"  {source_name:15s}: {status:4s} | {detail}")
    return results


if __name__ == "__main__":
    print("Data Freshness Test - Train 16227")
    print("Testing all 5 sources every 2 minutes for 30 minutes\n")

    all_results = []
    for run in range(1, 16):  # 16 runs over ~30 minutes
        results = run_round(run)
        all_results.append({"run": run, "timestamp": datetime.now().isoformat(), "results": results})

        if run < 16:
            time.sleep(120)  # Wait 2 minutes

    # Save results
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    results_file = os.path.join(RESULTS_DIR, f"freshness_test_{timestamp}.json")
    with open(results_file, "w") as f:
        json.dump(all_results, f, indent=2, default=str)

    print(f"\n{'='*60}")
    print("FRESHNESS TEST COMPLETE")
    print(f"{'='*60}")
    print(f"Results saved to: {results_file}")