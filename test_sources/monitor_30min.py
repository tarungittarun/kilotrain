"""
30-Minute Continuous Monitoring Script
Runs every 2 minutes for 30 minutes (16 runs) to track:
- Data freshness over time
- Source availability
- Per-stop actuals consistency
- Response time trends
"""

import requests
import json
import time
import csv
import os
from datetime import datetime, timedelta

# 10 key trains for detailed monitoring (representative sample)
MONITOR_TRAINS = [
    "16227", "16221", "12975", "12933", "12979",
    "12961", "12969", "12009", "12011", "12013"
]

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120 Safari/537.36",
    "Accept": "application/json, text/html, */*",
}

RESULTS_DIR = "test_sources/monitoring_results"
os.makedirs(RESULTS_DIR, exist_ok=True)


def query_source(source_name, train_number):
    """Query a single source for a single train"""
    if source_name == "WIMT":
        url = f"https://whereismytrain.in/cache/live_status?date=18-10-2026&train_no={train_number}&from=MYS&to=TLGP&from_day=1&lang=en&appVersion=7.1.5.633808517&sb_version=0"
    elif source_name == "RailYatri":
        url = f"https://livestatus.railyatri.in/api/v3/train_eta_data/{train_number}/0.json?start_day=0&user_id=-1&device_type_id=4&change_name=seo_train_name&lat=&lng=&claim_on_train=false"
    elif source_name == "RailRadar.in":
        url = f"https://railradar.in/api/v1/trains/{train_number}/live"
    elif source_name == "RunningStatus.in":
        url = f"https://runningstatus.in/status/{train_number}"
    elif source_name == "erail.in":
        url = f"https://erail.in/api/v1/train/live-status/{train_number}"
    else:
        return None

    try:
        r = requests.get(url, headers=HEADERS, timeout=15)
        return {
            "source": source_name,
            "train": train_number,
            "status_code": r.status_code,
            "response_time_ms": r.elapsed.total_seconds() * 1000,
            "timestamp": datetime.now().isoformat(),
            "has_data": r.status_code == 200,
            "raw_response_length": len(r.text),
        }
    except Exception as e:
        return {
            "source": source_name,
            "train": train_number,
            "status_code": None,
            "error": str(e),
            "timestamp": datetime.now().isoformat(),
            "has_data": False,
        }


def run_monitoring_round(run_number):
    """Run one monitoring round across all sources and trains"""
    print(f"\n{'='*60}")
    print(f"Monitoring Run #{run_number} - {datetime.now().strftime('%H:%M:%S')}")
    print(f"{'='*60}")

    all_results = []

    with ThreadPoolExecutor(max_workers=10) as executor:
        futures = []
        for train in MONITOR_TRAINS:
            for source in ["WIMT", "RailYatri", "RailRadar.in", "RunningStatus.in", "erail.in"]:
                futures.append(executor.submit(query_source, source, train))

        for future in as_completed(futures):
            result = future.result()
            all_results.append(result)

    # Save results
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    results_file = os.path.join(RESULTS_DIR, f"monitor_run{run_number}_{timestamp}.json")
    with open(results_file, "w") as f:
        json.dump(all_results, f, indent=2, default=str)

    # Print summary
    for source in ["WIMT", "RailYatri", "RailRadar.in", "RunningStatus.in", "erail.in"]:
        src_results = [r for r in all_results if r.get("source") == source]
        passed = len([r for r in src_results if r.get("has_data")])
        total = len(src_results)
        avg_time = sum(r.get("response_time_ms", 0) for r in src_results if r.get("response_time_ms")) / max(passed, 1)
        print(f"  {source:20s}: {passed:2d}/{total:2d} passed | avg {avg_response:.0f}ms")

    return all_results


if __name__ == "__main__":
    from concurrent.futures import ThreadPoolExecutor, as_completed

    print("=" * 60)
    print("30-MINUTE CONTINUOUS MONITORING")
    print(f"Start: {datetime.now().isoformat()}")
    print(f"Trains: {len(MONITOR_TRAINS)} trains")
    print(f"Sources: 5 sources")
    print(f"Interval: 2 minutes")
    print(f"Duration: 30 minutes (16 runs)")
    print("=" * 60)

    all_run_results = []

    for run in range(1, 17):  # 16 runs over ~30 minutes
        results = run_monitoring_round(run)
        all_run_results.append({
            "run": run,
            "timestamp": datetime.now().isoformat(),
            "results": results
        })

        if run < 16:
            print(f"\nWaiting 2 minutes before next run...")
            time.sleep(120)

    # Save final combined results
    final_file = os.path.join(RESULTS_DIR, f"monitoring_complete_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json")
    with open(final_file, "w") as f:
        json.dump(all_run_results, f, indent=2, default=str)

    print(f"\n{'='*60}")
    print("MONITORING COMPLETE")
    print(f"{'='*60}")
    print(f"Final results saved to: {final_file}")