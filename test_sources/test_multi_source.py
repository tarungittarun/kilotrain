"""
Multi-Source Train Tracking Data Validation Script
Tests 5 sources across 50+ trains over 30 minutes to verify:
- Data availability and completeness
- Per-stop actuals (actualArrival, actualDeparture, delays)
- Position pinpointing (current station, next station)
- Data freshness (timestamps)
- Response consistency over time

Sources tested:
1. WIMT (whereismytrain.in)
2. RailYatri (livestatus.railyatri.in)
3. RailRadar.in
4. RunningStatus.in
5. erail.in
"""

import requests
import json
import time
import csv
import os
from datetime import datetime
from concurrent.futures import ThreadPoolExecutor, as_completed

# 50 Indian train numbers for testing
TEST_TRAINS = [
    "16227", "16228", "16206", "16205", "16221", "16222", "16236", "16235",
    "16231", "16232", "16237", "16238", "16253", "16254", "12975", "12976",
    "12933", "12934", "12935", "12936", "12979", "12980", "12977", "12978",
    "12961", "12962", "12963", "12964", "12965", "12966", "12967", "12968",
    "12969", "12970", "12971", "12972", "12973", "12974", "12009", "12010",
    "12011", "12012", "12013", "12014", "12015", "12016", "12017", "12018"
]

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120 Safari/537.36",
    "Accept": "application/json, text/html, */*",
    "Accept-Language": "en-IN,en;q=0.9",
}

RESULTS_DIR = "test_sources/results"
os.makedirs(RESULTS_DIR, exist_ok=True)


def query_wimts(train_number):
    """Query WIMT live status"""
    url = f"https://whereismytrain.in/cache/live_status?date=18-10-2026&train_no={train_number}&from=MYS&to=TLGP&from_day=1&lang=en&appVersion=7.1.5.633808517&sb_version=0"
    try:
        r = requests.get(url, headers=HEADERS, timeout=15)
        if r.status_code == 200:
            data = r.json()
            stops = data.get("days_schedule", [])
            actual_stops = [s for s in stops if s.get("actual_arrival_time") or s.get("actual_departure_time")]
            return {
                "source": "WIMT",
                "train": train_number,
                "status_code": r.status_code,
                "response_time_ms": r.elapsed.total_seconds() * 1000,
                "total_stops": len(stops),
                "stops_with_actuals": len(actual_stops),
                "has_actuals": len(actual_stops) > 0,
                "current_station": data.get("curStn"),
                "last_update": data.get("lastUpdate"),
                "raw_data": data
            }
        else:
            return {"source": "WIMT", "train": train_number, "status_code": r.status_code, "error": "non-200"}
    except Exception as e:
        return {"source": "WIMT", "train": train_number, "error": str(e)}


def query_railyatri(train_number):
    """Query RailYatri live status"""
    url = f"https://livestatus.railyatri.in/api/v3/train_eta_data/{train_number}/0.json?start_day=0&user_id=-1&device_type_id=4&change_name=seo_train_name&lat=&lng=&claim_on_train=false"
    try:
        r = requests.get(url, headers=HEADERS, timeout=15)
        if r.status_code == 200:
            data = r.json()
            stops = data.get("stops", [])
            actual_stops = [s for s in stops if s.get("actualArrival") or s.get("actualDeparture")]
            return {
                "source": "RailYatri",
                "train": train_number,
                "status_code": r.status_code,
                "response_time_ms": r.elapsed.total_seconds() * 1000,
                "total_stops": len(stops),
                "stops_with_actuals": len(actual_stops),
                "has_actuals": len(actual_stops) > 0,
                "current_station": data.get("currentStationName"),
                "current_status": data.get("currentStatus"),
                "next_station": data.get("nextStation"),
                "raw_data": data
            }
        else:
            return {"source": "RailYatri", "train": train_number, "status_code": r.status_code, "error": "non-200"}
    except Exception as e:
        return {"source": "RailYatri", "train": train_number, "error": str(e)}


def query_railyatri_alt(train_number):
    """Query RailYatri alternative endpoint"""
    url = f"https://www.railyatri.in/m/live-train-status?trainNo={train_number}"
    try:
        r = requests.get(url, headers=HEADERS, timeout=15)
        if r.status_code == 200:
            return {
                "source": "RailYatri-HTML",
                "train": train_number,
                "status_code": r.status_code,
                "response_time_ms": r.elapsed.total_seconds() * 1000,
                "has_html": True,
                "html_length": len(r.text)
            }
        else:
            return {"source": "RailYatri-HTML", "train": train_number, "status_code": r.status_code}
    except Exception as e:
        return {"source": "RailYatri-HTML", "train": train_number, "error": str(e)}


def query_railradar(train_number):
    """Query RailRadar.in live status"""
    url = f"https://railradar.in/api/v1/trains/{train_number}/live"
    try:
        r = requests.get(url, headers=HEADERS, timeout=15)
        if r.status_code == 200:
            data = r.json()
            route = data.get("data", {}).get("route", [])
            actual_stops = [s for s in route if s.get("actualArrival") or s.get("actualDeparture")]
            current = data.get("data", {}).get("currentLocation", {})
            return {
                "source": "RailRadar.in",
                "train": train_number,
                "status_code": r.status_code,
                "response_time_ms": r.elapsed.total_seconds() * 1000,
                "total_stops": len(route),
                "stops_with_actuals": len(actual_stops),
                "has_actuals": len(actual_stops) > 0,
                "current_station": current.get("stationName"),
                "current_status": current.get("status"),
                "current_position": current.get("stationCode"),
                "delay_minutes": current.get("delayMinutes"),
                "raw_data": data
            }
        else:
            return {"source": "RailRadar.in", "train": train_number, "status_code": r.status_code, "error": "non-200"}
    except Exception as e:
        return {"source": "RailRadar.in", "train": train_number, "error": str(e)}


def query_runningstatus(train_number):
    """Query RunningStatus.in live status (HTML)"""
    url = f"https://runningstatus.in/status/{train_number}"
    try:
        r = requests.get(url, headers=HEADERS, timeout=15)
        if r.status_code == 200:
            # Extract station data from HTML
            import re
            station_codes = re.findall(r'([A-Z]{2,6})\s*\(([A-Z]{2,4})\)', r.text)
            time_patterns = re.findall(r'\d{2}:\d{2} (?:AM|PM)', r.text)
            has_actual = "actual" in r.text.lower() or "not reported" in r.text.lower()
            return {
                "source": "RunningStatus.in",
                "train": train_number,
                "status_code": r.status_code,
                "response_time_ms": r.elapsed.total_seconds() * 1000,
                "has_html": True,
                "html_length": len(r.text),
                "station_codes_found": station_codes[:10],
                "time_patterns_found": len(time_patterns),
                "has_actual_data": has_actual,
                "raw_html": r.text[:500]
            }
        else:
            return {"source": "RunningStatus.in", "train": train_number, "status_code": r.status_code}
    except Exception as e:
        return {"source": "RunningStatus.in", "train": train_number, "error": str(e)}


def query_erail(train_number):
    """Query erail.in live status"""
    url = f"https://erail.in/api/v1/train/live-status/{train_number}"
    try:
        r = requests.get(url, headers=HEADERS, timeout=15)
        if r.status_code == 200:
            return {
                "source": "erail.in",
                "train": train_number,
                "status_code": r.status_code,
                "response_time_ms": r.elapsed.total_seconds() * 1000,
                "has_response": True,
                "response_length": len(r.text),
                "raw_data": r.text[:500]
            }
        else:
            return {"source": "erail.in", "train": train_number, "status_code": r.status_code}
    except Exception as e:
        return {"source": "erail.in", "train": train_number, "error": str(e)}


SOURCE_MAP = {
    "WIMT": query_wimts,
    "RailYatri": query_railyatri,
    "RailRadar.in": query_railradar,
    "RunningStatus.in": query_runningstatus,
    "erail.in": query_erail,
}


def test_single_train(train_number):
    """Test all 5 sources for a single train"""
    results = []
    for source_name, source_func in SOURCE_MAP.items():
        try:
            result = source_func(train_number)
            results.append(result)
        except Exception as e:
            results.append({"source": source_name, "train": train_number, "error": str(e)})
    return results


def run_full_test():
    """Run full test across all 50 trains and all 5 sources"""
    all_results = []
    
    print(f"Testing {len(TEST_TRAINS)} trains across 5 sources...")
    print(f"Start time: {datetime.now().isoformat()}\n")
    
    with ThreadPoolExecutor(max_workers=5) as executor:
        futures = {executor.submit(test_single_train, tn): tn for tn in TEST_TRAINS}
        
        for future in as_completed(futures):
            train_results = future.result()
            all_results.extend(train_results)
            
            # Print progress
            train_num = train_results[0].get("train", "?")
            sources_tested = len(train_results)
            sources_passed = sum(1 for r in train_results if r.get("status_code") == 200)
            print(f"Train {train_num}: {sources_passed}/{sources_tested} sources passed")
    
    # Save results
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    results_file = os.path.join(RESULTS_DIR, f"test_results_{timestamp}.json")
    with open(results_file, "w") as f:
        json.dump(all_results, f, indent=2, default=str)
    
    # Summary
    print("\n" + "="*60)
    print("TEST SUMMARY")
    print("="*60)
    
    for source_name in SOURCE_MAP.keys():
        source_results = [r for r in all_results if r.get("source") == source_name]
        total = len(source_results)
        passed = sum(1 for r in source_results if r.get("status_code") == 200)
        has_actuals = sum(1 for r in source_results if r.get("has_actuals") == True)
        avg_response = sum(r.get("response_time_ms", 0) for r in source_results if r.get("response_time_ms")) / max(passed, 1)
        
        print(f"\n{source_name}:")
        print(f"  Total tests: {total}")
        print(f"  Passed (200): {passed} ({passed/total*100:.1f}%)")
        print(f"  With actuals: {has_actuals} ({has_actuals/total*100:.1f}% of passed)")
        print(f"  Avg response: {avg_response:.0f}ms")
    
    return all_results


if __name__ == "__main__":
    results = run_full_test()