"""
Multi-Source Data Abstraction Layer

Each source implements the SourceInterface with:
- fetch_live_status(train_number) -> TrainStatus
- fetch_pair_search(from_station, to_station) -> List[TrainListing]
- health_check() -> bool

All sources return normalized data via the models module.
"""

from typing import List, Optional, Dict, Any
from datetime import datetime
import requests
import json
import re
import time
import logging

from models import (
    TrainStatus, TrainListing, StopStatus, PositionInfo,
    SourceContribution, SourceName, DataFreshness,
    now_millis, now_iso, compute_data_freshness,
    compute_position_freshness, train_status_to_dict,
    train_listing_to_dict
)

logger = logging.getLogger(__name__)

DEFAULT_TIMEOUT = 15
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120 Safari/537.36",
    "Accept": "application/json, text/html, */*",
    "Accept-Language": "en-IN,en;q=0.9",
}


class SourceInterface:
    """Base class for all train data sources"""
    
    name: str = "base"
    is_healthy: bool = True
    consecutive_failures: int = 0
    total_requests: int = 0
    successful_requests: int = 0
    avg_response_ms: float = 0.0
    
    def fetch_live_status(self, train_number: str) -> TrainStatus:
        raise NotImplementedError
    
    def fetch_pair_search(self, from_station: str, to_station: str) -> List[TrainListing]:
        raise NotImplementedError
    
    def health_check(self) -> bool:
        try:
            result = self.fetch_live_status("16227")
            return result is not None
        except Exception:
            return False
    
    def record_success(self, response_ms: float):
        self.total_requests += 1
        self.successful_requests += 1
        self.consecutive_failures = 0
        self.is_healthy = True
        self.avg_response_ms = (self.avg_response_ms * (self.total_requests - 1) + response_ms) / self.total_requests
    
    def record_failure(self):
        self.total_requests += 1
        self.consecutive_failures += 1
        if self.consecutive_failures >= 3:
            self.is_healthy = False


class WIMTSource(SourceInterface):
    """whereismytrain.in - Full per-stop actuals with Unix timestamps"""
    
    name = SourceName.WIMT
    
    def fetch_live_status(self, train_number: str) -> TrainStatus:
        # Use today's date for the request
        today = datetime.now(timezone.utc).strftime("%d-%m-%Y")
        url = (
            f"https://whereismytrain.in/cache/live_status?"
            f"date={today}&train_no={train_number}"
            f"&from=MYS&to=TLGP&from_day=1&lang=en"
            f"&appVersion=7.1.5.633808517&sb_version=0"
        )
        start = now_millis()
        try:
            r = requests.get(url, headers=HEADERS, timeout=DEFAULT_TIMEOUT)
            if r.status_code != 200:
                self.record_failure()
                return TrainStatus(
                    train_number=train_number, train_name="",
                    source_station_code="", source_station_name="",
                    dest_station_code="", dest_station_name="",
                    current_status="source_unavailable",
                    source_label="WIMT",
                    response_time_ms=int((now_millis() - start)),
                    fetched_at_millis=now_millis(),
                    data_freshness="unknown",
                )
            
            data = r.json()
            stops = data.get("days_schedule", [])
            
            stop_list = []
            for s in stops:
                stop_list.append(StopStatus(
                    code=s.get("station_code", ""),
                    name=s.get("station_name", ""),
                    scheduled_arrival=s.get("sch_arrival_time"),
                    scheduled_departure=s.get("sch_departure_time"),
                    actual_arrival=s.get("actual_arrival_time"),
                    actual_departure=s.get("actual_departure_time"),
                    arrival_delay_minutes=s.get("delay_in_arrival"),
                    departure_delay_minutes=s.get("delay_in_departure"),
                    platform=s.get("platform"),
                    distance_km=s.get("distance"),
                    is_current_station=s.get("curStn", False),
                    is_departed_here=s.get("departed", False),
                    source_provenance="WIMT",
                    km_to_next=None,
                    expected_departure=None,
                    actual_arrival_tm=s.get("actual_arrival_tm"),
                    actual_departure_tm=s.get("actual_departure_tm"),
                ))
            
            # Find current station
            current_station = None
            current_station_name = None
            for s in stops:
                if s.get("curStn"):
                    current_station = s.get("station_code")
                    current_station_name = s.get("station_name")
                    break
            
            resp_ms = (now_millis() - start)
            self.record_success(resp_ms)
            
            return TrainStatus(
                train_number=train_number,
                train_name=data.get("train_name", ""),
                source_station_code=data.get("source_station", ""),
                source_station_name=data.get("source_station_name", ""),
                dest_station_code=data.get("destination_station", ""),
                dest_station_name=data.get("destination_station_name", ""),
                current_status=data.get("currentStatus", "unknown"),
                current_station_name=current_station_name,
                stops=stop_list,
                source_label="WIMT live running status",
                source_contributions=[SourceContribution(
                    source="WIMT",
                    fetched_at_millis=now_millis(),
                    stops_covered=len(stops),
                    has_actuals=any(s.actual_arrival or s.actual_departure for s in stop_list),
                )],
                response_time_ms=int(resp_ms),
                fetched_at_millis=now_millis(),
                data_freshness=compute_data_freshness(now_millis()),
            )
        except Exception as e:
            self.record_failure()
            return TrainStatus(
                train_number=train_number, train_name="",
                source_station_code="", source_station_name="",
                dest_station_code="", dest_station_name="",
                current_status="error",
                source_label="WIMT",
                response_time_ms=int((now_millis() - start)),
                fetched_at_millis=now_millis(),
                data_freshness="unknown",
            )
    
    def fetch_pair_search(self, from_station: str, to_station: str) -> List[TrainListing]:
        return []


class RailRadarSource(SourceInterface):
    """railradar.in - Full per-stop actuals with ISO timestamps"""
    
    name = SourceName.RAILRADAR
    
    def fetch_live_status(self, train_number: str) -> TrainStatus:
        url = f"https://railradar.in/api/v1/trains/{train_number}/live"
        start = now_millis()
        try:
            r = requests.get(url, headers=HEADERS, timeout=DEFAULT_TIMEOUT)
            if r.status_code != 200:
                self.record_failure()
                return TrainStatus(
                    train_number=train_number, train_name="",
                    source_station_code="", source_station_name="",
                    dest_station_code="", dest_station_name="",
                    current_status="source_unavailable",
                    source_label="RailRadar.in",
                    response_time_ms=int((now_millis() - start)),
                    fetched_at_millis=now_millis(),
                    data_freshness="unknown",
                )
            
            data = r.json()
            route = data.get("data", {}).get("route", [])
            current = data.get("data", {}).get("currentLocation", {})
            
            stop_list = []
            for s in route:
                stop_list.append(StopStatus(
                    code=s.get("stationCode", ""),
                    name=s.get("stationName", ""),
                    scheduled_arrival=s.get("scheduledArrival", "").split("T")[1][:5] if s.get("scheduledArrival") else None,
                    scheduled_departure=s.get("scheduledDeparture", "").split("T")[1][:5] if s.get("scheduledDeparture") else None,
                    actual_arrival=s.get("actualArrival", "").split("T")[1][:5] if s.get("actualArrival") else None,
                    actual_departure=s.get("actualDeparture", "").split("T")[1][:5] if s.get("actualDeparture") else None,
                    arrival_delay_minutes=s.get("delayArrival"),
                    departure_delay_minutes=s.get("delayDeparture"),
                    platform=s.get("platform"),
                    distance_km=s.get("distance"),
                    is_current_station=s.get("status") == "at-station",
                    is_departed_here=s.get("status") == "departed",
                    source_provenance="RailRadar.in",
                    km_to_next=None,
                    expected_departure=None,
                    actual_arrival_tm=None,
                    actual_departure_tm=None,
                ))
            
            current_station = current.get("stationCode")
            current_station_name = current.get("stationName")
            
            resp_ms = (now_millis() - start)
            self.record_success(resp_ms)
            
            return TrainStatus(
                train_number=data.get("train", {}).get("number", train_number),
                train_name=data.get("data", {}).get("trainName", ""),
                source_station_code=data.get("data", {}).get("source", {}).get("code", ""),
                source_station_name=data.get("data", {}).get("source", {}).get("name", ""),
                dest_station_code=data.get("data", {}).get("destination", {}).get("code", ""),
                dest_station_name=data.get("data", {}).get("destination", {}).get("name", ""),
                current_status=data.get("data", {}).get("status", "unknown"),
                current_station_name=current_station_name,
                stops=stop_list,
                source_label="RailRadar.in live running status",
                source_contributions=[SourceContribution(
                    source="RailRadar.in",
                    fetched_at_millis=now_millis(),
                    stops_covered=len(stops),
                    has_actuals=any(s.actual_arrival or s.actual_departure for s in stop_list),
                )],
                response_time_ms=int(resp_ms),
                fetched_at_millis=now_millis(),
                data_freshness=compute_data_freshness(now_millis()),
            )
        except Exception as e:
            self.record_failure()
            return TrainStatus(
                train_number=train_number, train_name="",
                source_station_code="", source_station_name="",
                dest_station_code="", dest_station_name="",
                current_status="error",
                source_label="RailRadar.in",
                response_time_ms=int((now_millis() - start)),
                fetched_at_millis=now_millis(),
                data_freshness="unknown",
            )
    
    def fetch_pair_search(self, from_station: str, to_station: str) -> List[TrainListing]:
        return []


class RunningStatusSource(SourceInterface):
    """runningstatus.in - Full per-stop from HTML"""
    
    name = SourceName.RUNNINGSTATUS
    
    def fetch_live_status(self, train_number: str) -> TrainStatus:
        url = f"https://runningstatus.in/status/{train_number}"
        start = now_millis()
        try:
            r = requests.get(url, headers=HEADERS, timeout=DEFAULT_TIMEOUT)
            if r.status_code != 200:
                self.record_failure()
                return TrainStatus(
                    train_number=train_number, train_name="",
                    source_station_code="", source_station_name="",
                    dest_station_code="", dest_station_name="",
                    current_status="source_unavailable",
                    source_label="RunningStatus.in",
                    response_time_ms=int((now_millis() - start)),
                    fetched_at_millis=now_millis(),
                    data_freshness="unknown",
                )
            
            # Extract station data from HTML table rows
            import re
            html = r.text
            
            stop_list = []
            # Look for table rows with station data
            # runningstatus.in has table rows with station info
            rows = re.findall(r'<tr[^>]*>(.*?)</tr>', html, re.DOTALL)
            for row in rows:
                # Extract station code and name
                code_match = re.search(r'([A-Z]{2,6})\s*\(([A-Z]{2,4})\)', row)
                if code_match:
                    code = code_match.group(1)
                    name = code_match.group(2)
                    # Look for times in the row
                    times = re.findall(r'\d{2}:\d{2}', row)
                    scheduled_arrival = times[0] if len(times) > 0 else None
                    scheduled_departure = times[1] if len(times) > 1 else None
                    actual_arrival = times[2] if len(times) > 2 else None
                    actual_departure = times[3] if len(times) > 3 else None
                    
                    stop_list.append(StopStatus(
                        code=code,
                        name=name,
                        scheduled_arrival=scheduled_arrival,
                        scheduled_departure=scheduled_departure,
                        actual_arrival=actual_arrival,
                        actual_departure=actual_departure,
                        source_provenance="RunningStatus.in",
                    ))
            
            resp_ms = (now_millis() - start)
            self.record_success(resp_ms)
            
            return TrainStatus(
                train_number=train_number,
                train_name="",
                source_station_code="",
                source_station_name="",
                dest_station_code="",
                dest_station_name="",
                current_status="live",
                current_station_name=None,
                stops=stop_list,
                source_label="RunningStatus.in live running status",
                source_contributions=[SourceContribution(
                    source="RunningStatus.in",
                    fetched_at_millis=now_millis(),
                    stops_covered=len(stop_list),
                    has_actuals=any(s.actual_arrival or s.actual_departure for s in stop_list),
                )],
                response_time_ms=int(resp_ms),
                fetched_at_millis=now_millis(),
                data_freshness=compute_data_freshness(now_millis()),
            )
        except Exception as e:
            self.record_failure()
            return TrainStatus(
                train_number=train_number, train_name="",
                source_station_code="", source_station_name="",
                dest_station_code="", dest_station_name="",
                current_status="error",
                source_label="RunningStatus.in",
                response_time_ms=int((now_millis() - start)),
                fetched_at_millis=now_millis(),
                data_freshness="unknown",
            )
    
    def fetch_pair_search(self, from_station: str, to_station: str) -> List[TrainListing]:
        return []


class ErailSource(SourceInterface):
    """erail.in - Schedule only (fallback)"""
    
    name = SourceName.ERAIL
    
    def fetch_live_status(self, train_number: str) -> TrainStatus:
        url = f"https://erail.in/api/v1/train/live-status/{train_number}"
        start = now_millis()
        try:
            r = requests.get(url, headers=HEADERS, timeout=DEFAULT_TIMEOUT)
            if r.status_code != 200:
                self.record_failure()
                return TrainStatus(
                    train_number=train_number, train_name="",
                    source_station_code="", source_station_name="",
                    dest_station_code="", dest_station_name="",
                    current_status="source_unavailable",
                    source_label="erail.in",
                    response_time_ms=int((now_millis() - start)),
                    fetched_at_millis=now_millis(),
                    data_freshness="unknown",
                )
            
            resp_ms = (now_millis() - start)
            self.record_success(resp_ms)
            
            return TrainStatus(
                train_number=train_number, train_name="",
                source_station_code="", source_station_name="",
                dest_station_code="", dest_station_name="",
                current_status="schedule_only",
                current_station_name=None,
                stops=[],
                source_label="erail.in schedule",
                source_contributions=[SourceContribution(
                    source="erail.in",
                    fetched_at_millis=now_millis(),
                    stops_covered=0,
                    has_actuals=False,
                )],
                response_time_ms=int(resp_ms),
                fetched_at_millis=now_millis(),
                data_freshness=compute_data_freshness(now_millis()),
            )
        except Exception as e:
            self.record_failure()
            return TrainStatus(
                train_number=train_number, train_name="",
                source_station_code="", source_station_name="",
                dest_station_code="", dest_station_name="",
                current_status="error",
                source_label="erail.in",
                response_time_ms=int((now_millis() - start)),
                fetched_at_millis=now_millis(),
                data_freshness="unknown",
            )
    
    def fetch_pair_search(self, from_station: str, to_station: str) -> List[TrainListing]:
        url = f"https://erail.in/rail/getTrains.aspx?TrainNo={from_station}&from={from_station}&to={to_station}"
        try:
            r = requests.get(url, headers=HEADERS, timeout=DEFAULT_TIMEOUT)
            if r.status_code != 200:
                return []
            
            text = r.text
            trains = []
            parts = text.split('~')
            i = 0
            while i < len(parts) - 10:
                if parts[i] and parts[i].isdigit() and len(parts[i]) in (5, 6):
                    trains.append(TrainListing(
                        train_number=parts[i],
                        train_name=parts[i+1] if i+1 < len(parts) else "",
                        source_station_code=parts[i+2] if i+2 < len(parts) else "",
                        source_station_name=parts[i+3] if i+3 < len(parts) else "",
                        dest_station_code=parts[i+4] if i+4 < len(parts) else "",
                        dest_station_name=parts[i+5] if i+5 < len(parts) else "",
                        scheduled_departure=parts[i+6] if i+6 < len(parts) else None,
                        scheduled_arrival=parts[i+7] if i+7 < len(parts) else None,
                        source="erail",
                    ))
                    i += 8
                else:
                    i += 1
            return trains
        except Exception as e:
            return []


class NTESource(SourceInterface):
    """NTES (enquiry.indianrail.gov.in) - Device-only source"""
    
    name = SourceName.NTES
    
    def fetch_live_status(self, train_number: str) -> TrainStatus:
        # NTES is blocked from sandbox but works on device
        return TrainStatus(
            train_number=train_number, train_name="",
            source_station_code="", source_station_name="",
            dest_station_code="", dest_station_name="",
            current_status="device_only",
            source_label="NTES (device only)",
            response_time_ms=0,
            fetched_at_millis=now_millis(),
            data_freshness="unknown",
        )
    
    def fetch_pair_search(self, from_station: str, to_station: str) -> List[TrainListing]:
        return []
