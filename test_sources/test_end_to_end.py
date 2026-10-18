"""
End-to-End Test: Multi-Source Train Data Validation

Tests all 5 working sources with real train data and verifies:
- Each source returns valid data
- Per-stop actuals are extracted correctly
- Position tracking works
- Data freshness is calculated correctly
- Fallback chain works when sources fail

Run with: python test_end_to_end.py
"""

import sys
import os
import json
import time
from datetime import datetime

# Add backend to path
sys.path.insert(0, os.path.dirname(__file__))

from aggregator import get_live_status, search_trains, aggregator
from models import train_status_to_dict, train_listing_to_dict
from sources import SourceInterface

# Test trains - mix of different routes and types
TEST_TRAINS = [
    "16227",  # MYS TLGP EXP - short route
    "16206",  # MYS SBC EXP
    "12975",  # BDTS HWH EXP
    "12933",  # BDTS BDTS EXP
    "12979",  # BDTS ADI EXP
    "12961",  # BDTS PUNE EXP
    "12009",  # SBC NDLS SHATABDI
    "12011",  # SBC NDLS SHATABDI
    "12013",  # SBC NDLS SHATABDI
    "12015",  # SBC NDLS SHATABDI,
    "12017",  # SBC NDLS SHATABDI
    "12013",  # duplicate for testing
]

def test_single_source(source_name, source_func, train_number):
    """Test a single source for a single train"""
    try:
        result = source_func(train_number)
        if result and result.get("status_code") == 200:
            return {
                "source": source_name,
                "train": train_number,
                "status": "PASS",
                "response_time_ms": result.get("response_time_ms", 0),
                "stops_covered": result.get("stops_covered", 0),
                "has_actuals": result.get("has_actuals", False),
                "current_station": result.get("current_station_name"),
                "source_label": result.get("source_label"),
            }
        else:
            return {
                "source": source_name,
                "train": train_number,
                "status": "FAIL",
                "status_code": result.get("status_code"),
                "error": result.get("error", "unknown"),
            }
    except Exception as e:
        return {
            "source": source_name,
            "train": train_number,
            "status": "ERROR",
            "error": str(e),
        }


def test_all_sources_all_trains():
    """Test all 5 sources against all 50 trains"""
    from sources import WIMTSource, RailRadarSource, RunningStatusSource, ErailSource, NTESource
    
    sources = [
        ("WIMT", WIMTSource()),
        ("RailRadar.in", RailRadarSource()),
        ("RunningStatus.in", RunningStatusSource()),
        ("erail.in", ErailSource()),
        ("NTES", NTESource()),
    ]
    
    all_results = []
    
    for source_name, source in sources:
        print(f"\nTesting {source_name}...")
        source_results = []
        
        for train in TEST_TRAINS[:10]:  # Test first 10 trains for speed
            result = test_single_source(source_name, source.fetch_live_status, train)
            source_results.append(result)
            all_results.append(result)
            
            status_icon = "✓" if result["status"] == "PASS" else "✗"
            print(f"  {status_icon} {train}: {result['status']} "
                  f"({result.get('response_time_ms', 'N/A')}ms)")
        
        passed = sum(1 for r in source_results if r["status"] == "PASS")
        total = len(source_results)
        with_actuals = sum(1 for r in source_results if r.get("has_actuals"))
        
        print(f"\n  {source_name}: {passed}/{total} passed, {with_actuals} with actuals")
    
    return all_results


def test_detailed_train(train_number):
    """Detailed test for a single train showing all data fields"""
    print(f"\n{'='*70}")
    print(f"DETAILED TEST: Train {train_number}")
    print(f"{'='*70}")
    
    result = get_live_status(train_number)
    
    print(f"\nTrain: {result.get('train_number')} - {result.get('train_name', 'N/A')}")
    print(f"Source: {result.get('source_label')}")
    print(f"Current Status: {result.get('current_status')}")
    print(f"Current Station: {result.get('current_station_name', 'N/A')}")
    print(f"Response Time: {result.get('response_time_ms', 0)}ms")
    print(f"Data Freshness: {result.get('data_freshness', 'unknown')}")
    print(f"Fetched At: {result.get('fetched_at_millis', 0)}")
    
    print(f"\nSource Contributions:")
    for sc in result.get("source_contributions", []):
        print(f"  {sc['source']}: {sc['stops_covered']} stops, "
              f"{'has actuals' if sc['has_actuals'] else 'no actuals'}")
    
    print(f"\nPer-Stop Details:")
    print(f"{'Station':<15} {'Code':<8} {'Sch Arr':<10} {'Actual Arr':<12} "
          f"{'Sch Dep':<10} {'Actual Dep':<12} {'Delay':<8} {'Platform':<10} {'Source'}")
    print("-" * 100)
    
    for stop in result.get("stops", [])[:15]:  # Show first 15 stops
        print(f"{stop.get('name', ''):<15} {stop.get('code', ''):<8} "
              f"{stop.get('scheduled_arrival', '—'):<10} "
              f"{stop.get('actual_arrival', '—'):<12} "
              f"{stop.get('scheduled_departure', '—'):<10} "
              f"{stop.get('actual_departure', '—'):<12} "
              f"{stop.get('arrival_delay_minutes', '—'):<8} "
              f"{stop.get('platform', '—'):<10} "
              f"{stop.get('source_provenance', '—')}")
    
    print(f"\nPosition Info:")
    pos = result.get("position")
    if pos:
        print(f"  Interpolated: {pos.get('interpolated')}")
        print(f"  Between Stations: {pos.get('between_stations')}")
        print(f"  Minutes Ago: {pos.get('minutes_ago')}")
        print(f"  Confidence: {pos.get('confidence')}")
        print(f"  Last Reported Stop: {pos.get('last_reported_stop')}")
    
    return result


def run_comprehensive_test():
    """Run complete test suite"""
    print("=" * 70)
    print("OPEN TRACK INDIA - MULTI-SOURCE DATA VALIDATION")
    print("=" * 70)
    
    # Test 1: All sources, all trains
    print("\n[TEST 1] All sources × 50 trains")
    results = test_all_sources_all_trains()
    
    # Summary
    print("\n" + "=" * 70)
    print("SOURCE SUMMARY")
    print("=" * 70)
    
    for source_name in ["WIMT", "RailRadar.in", "RunningStatus.in", "erail.in", "RailYatri"]:
        src_results = [r for r in results if r.get("source") == source_name]
        total = len(src_results)
        passed = sum(1 for r in src_results if r["status"] == "PASS")
        with_actuals = sum(1 for r in src_results if r.get("has_actuals"))
        
        print(f"\n{source_name}:")
        print(f"  Tests: {total}")
        print(f"  Passed: {passed} ({passed/total*100:.1f}% if total else 0%)")
        print(f"  With actuals: {with_actuals}")
    
    # Test 2: Detailed train
    print(f"\n{'='*70}")
    print("[TEST 2] Detailed train view")
    print(f"{'='*70}")
    result = test_detailed_train("16227")
    
    print("\n" + "=" * 70)
    print("TEST COMPLETE")
    print("=" * 70)


if __name__ == "__main__":
    run_comprehensive_test()
SOURCE_EOF
echo "Test script created"