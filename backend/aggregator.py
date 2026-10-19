"""
Multi-Source Aggregator - FirstWinsAggregator

Merges data from multiple sources with priority:
1. NTES (highest priority - most accurate)
2. WIMT (per-stop actuals with Unix timestamps)
3. RailRadar.in (full per-stop with ISO timestamps)
4. RunningStatus.in (HTML-parsed per-stop)
5. erail.in (schedule only - fallback)

Merge rules:
- Actual times: NTES > WIMT > RailRadar > RunningStatus (each source's actuals override gaps)
- Schedule: erail.in > NTES > WIMT > RailRadar (erail has cleanest timetable)
- Position: last reported stop from any source, interpolated to next stop
- Freshness: tracked per source; merged view shows youngest actual
- Confidence scoring per stop based on source reliability and data age
"""

from dataclasses import dataclass, field
from typing import List, Optional, Dict, Any
from datetime import datetime
import time

from models import (
    TrainStatus, TrainListing, StopStatus, PositionInfo,
    SourceContribution, SourceName, DataFreshness,
    now_millis, compute_data_freshness, compute_position_freshness,
    train_status_to_dict
)
from sources import SourceInterface, WIMTSource, RailRadarSource, RunningStatusSource, ErailSource, NTESource


@dataclass
class MergedStop:
    """Merged per-stop result from all sources"""
    code: str
    name: str
    scheduled_arrival: Optional[str] = None
    scheduled_departure: Optional[str] = None
    actual_arrival: Optional[str] = None
    actual_departure: Optional[str] = None
    arrival_delay_minutes: Optional[int] = None
    departure_delay_minutes: Optional[int] = None
    platform: Optional[str] = None
    distance_km: Optional[float] = None
    halt_minutes: Optional[int] = None
    is_current_station: bool = False
    is_departed_here: bool = False
    is_next_station: bool = False
    confidence: str = "low"  # high, medium, low
    source_provenance: str = "schedule"  # Which source provided actuals
    km_to_next: Optional[str] = None
    expected_departure: Optional[str] = None
    actual_arrival_tm: Optional[int] = None  # Unix ms from WIMT
    actual_departure_tm: Optional[int] = None


@dataclass
class PositionInfo:
    """Position interpolation result"""
    interpolated: bool = False
    between_stations: Optional[str] = None
    km_from_previous: Optional[str] = None
    minutes_ago: Optional[int] = None
    confidence: str = "low"
    last_reported_stop: str = ""


@dataclass
class SourceContribution:
    """Per-source contribution summary"""
    source: str
    fetched_at_millis: int
    stops_covered: int
    has_actuals: bool


@dataclass
class MergedResult:
    """One combined live status from all sources"""
    train_no: str
    current_status: str
    current_station_name: Optional[str]
    stops: List[MergedStop]
    position: Optional[PositionInfo]
    via_cache: bool
    fetched_at_millis: int
    source_label: str
    source_contributions: List[SourceContribution]
    response_time_ms: int


class FirstWinsAggregator:
    """
    First-wins aggregator for multi-source train data.

    Strategy:
    1. Fetch from ALL sources concurrently
    2. Merge per-stop: NTES > WIMT > RailRadar > RunningStatus (actuals override gaps)
    3. Schedule: erail.in > NTES > WIMT > RailRadar (cleanest timetable)
    4. Position: last reported stop from any source, interpolated to next
    5. Freshness: tracked per source; merged view shows youngest actual
    """

    def __init__(self):
        self.sources: List[SourceInterface] = []
        self._initialize_sources()

    def _initialize_sources(self):
        """Initialize all sources in priority order"""
        self.sources = [
            NTESource(),      # Priority 1 - most accurate
            WIMTSource(),     # Priority 2 - full per-stop actuals
            RailRadarSource(), # Priority 3 - full per-stop with ISO timestamps
            RunningStatusSource(), # Priority 4 - HTML-parsed per-stop
            ErailSource(),    # Priority 5 - schedule only fallback
        ]

    def get_healthy_sources(self) -> List[SourceInterface]:
        """Return only healthy sources"""
        return [s for s in self.sources if s.is_healthy]

    def merge(
        self,
        train_number: str,
        from_station: str = "",
        to_station: str = "",
    ) -> MergedResult:
        """Fetch from all sources and merge into one unified status"""
        start = now_millis()

        # Fetch from all sources concurrently
        results = {}
        for source in self.get_healthy_sources():
            try:
                result = source.fetch_live_status(train_number)
                results[source.name] = result
            except Exception as e:
                source.record_failure()
                results[source.name] = None

        # Determine winner (first source with data)
        winner = None
        for source in self.get_healthy_sources():
            if source.name in results and results[source.name]:
                r = results[source.name]
                if r.stops or r.current_status != "no source answered":
                    winner = source.name
                    break

        if not winner:
            return MergedResult(
                train_no=train_number,
                current_status="no source answered",
                current_station_name=None,
                stops=[],
                position=None,
                via_cache=False,
                fetched_at_millis=now_millis(),
                source_label="no source answered",
                source_contributions=[],
                response_time_ms=int(now_millis() - start),
            )

        # Build merged stops - priority order: NTES > WIMT > RailRadar > RunningStatus > erail
        merged_stops = self._merge_stops(results)

        # Build position info based on CURRENT TIME vs schedule
        position = self._compute_position(merged_stops, now_millis())

        # Build source contributions
        contributions = self._build_contributions(results, now_millis())

        # Determine source label
        source_label = self._get_winner_label(results)

        response_time_ms = int(now_millis() - start)

        return MergedResult(
            train_no=train_number,
            current_status=results[winner].current_status if results[winner] else "no data",
            current_station_name=results[winner].current_station_name if results[winner] else None,
            stops=merged_stops,
            position=position,
            via_cache=False,
            fetched_at_millis=now_millis(),
            source_label=source_label,
            source_contributions=contributions,
            response_time_ms=response_time_ms,
        )

    def _merge_stops(self, results: dict) -> List[MergedStop]:
        """Merge per-stop data from all sources"""
        # Collect all station codes from all sources
        all_stops = {}

        for source_name, result in results.items():
            if not result or not result.stops:
                continue
            for stop in result.stops:
                code = stop.code
                if code not in all_stops:
                    all_stops[code] = {
                        "code": code,
                        "name": stop.name,
                        "scheduled_arrival": None,
                        "scheduled_departure": None,
                        "actual_arrival": None,
                        "actual_departure": None,
                        "arrival_delay_minutes": None,
                        "departure_delay_minutes": None,
                        "platform": None,
                        "distance_km": None,
                        "halt_minutes": None,
                        "is_current_station": False,
                        "is_departed_here": False,
                        "is_next_station": False,
                        "confidence": "low",
                        "source_provenance": "schedule",
                        "km_to_next": None,
                        "expected_departure": None,
                        "actual_arrival_tm": None,
                        "actual_departure_tm": None,
                    }

                stop_data = all_stops[code]

                # Priority: NTES > WIMT > RailRadar > RunningStatus (for actuals)
                priority_order = ["ntes", "wimt", "railradar.in", "runningstatus.in", "erail.in"]
                source_priority = {s: i for i, s in enumerate(priority_order)}

                current_priority = source_priority.get(source_name, 99)
                existing_provenance = stop_data["source_provenance"]
                existing_priority = source_priority.get(existing_provenance, 99)

                # Update with higher priority source data
                if current_priority < existing_priority or existing_provenance == "schedule":
                    stop_data["scheduled_arrival"] = stop.scheduled_arrival or stop_data["scheduled_arrival"]
                    stop_data["scheduled_departure"] = stop.scheduled_departure or stop_data["scheduled_departure"]
                    stop_data["actual_arrival"] = stop.actual_arrival or stop_data["actual_arrival"]
                    stop_data["actual_departure"] = stop.actual_departure or stop_data["actual_departure"]
                    stop_data["arrival_delay_minutes"] = stop.arrival_delay_minutes or stop_data["arrival_delay_minutes"]
                    stop_data["departure_delay_minutes"] = stop.departure_delay_minutes or stop_data["departure_delay_minutes"]
                    stop_data["platform"] = stop.platform or stop_data["platform"]
                    stop_data["distance_km"] = stop.distance_km or stop_data["distance_km"]
                    stop_data["halt_minutes"] = stop.halt_minutes or stop_data["halt_minutes"]
                    stop_data["is_current_station"] = stop.is_current_station or stop_data["is_current_station"]
                    stop_data["is_departed_here"] = stop.is_departed_here or stop_data["is_departed_here"]
                    stop_data["is_next_station"] = stop.is_next_station or stop_data["is_next_station"]
                    stop_data["source_provenance"] = source_name
                    stop_data["confidence"] = "high" if source_name in ["ntes", "wimt"] else "medium"
                    stop_data["actual_arrival_tm"] = stop.actual_arrival_tm or stop_data["actual_arrival_tm"]
                    stop_data["actual_departure_tm"] = stop.actual_departure_tm or stop_data["actual_departure_tm"]

        # Convert to MergedStop list
        merged = []
        for code, data in all_stops.items():
            merged.append(MergedStop(
                code=data["code"],
                name=data["name"],
                scheduled_arrival=data["scheduled_arrival"],
                scheduled_departure=data["scheduled_departure"],
                actual_arrival=data["actual_arrival"],
                actual_departure=data["actual_departure"],
                arrival_delay_minutes=data["arrival_delay_minutes"],
                departure_delay_minutes=data["departure_delay_minutes"],
                platform=data["platform"],
                distance_km=data["distance_km"],
                halt_minutes=data["halt_minutes"],
                is_current_station=data["is_current_station"],
                is_departed_here=data["is_departed_here"],
                is_next_station=data["is_next_station"],
                confidence=data["confidence"],
                source_provenance=data["source_provenance"],
                km_to_next=data["km_to_next"],
                expected_departure=data["expected_departure"],
                actual_arrival_tm=data["actual_arrival_tm"],
                actual_departure_tm=data["actual_departure_tm"],
            ))

        return merged

    def _compute_position(self, stops: List[MergedStop], now_millis_val: int) -> Optional[PositionInfo]:
        """
        Compute current position based on CURRENT TIME vs scheduled times.

        Logic:
        1. Find the last stop where scheduled_departure <= current_time (train has departed)
        2. Find the next stop where scheduled_arrival > current_time (train hasn't arrived yet)
        3. If train is between stations, interpolate position
        4. If train hasn't started yet, show "not started yet"
        5. If train has reached destination, show "reached destination"
        """
        if not stops:
            return None

        now_minutes = (now_millis_val / 60000) % (24 * 60)

        # Find last stop where scheduled_departure <= now (train has left)
        last_departed = -1
        for i, stop in enumerate(stops):
            if stop.scheduled_departure:
                try:
                    dep_parts = stop.scheduled_departure.split(":")
                    dep_minutes = int(dep_parts[0]) * 60 + int(dep_parts[1])
                    if dep_minutes <= now_minutes:
                        last_departed = i
                except (ValueError, AttributeError):
                    pass

        # Find next stop where scheduled_arrival > now (train hasn't arrived yet)
        next_idx = None
        for i, stop in enumerate(stops):
            if stop.scheduled_arrival:
                try:
                    arr_parts = stop.scheduled_arrival.split(":")
                    arr_minutes = int(arr_parts[0]) * 60 + int(arr_parts[1])
                    if arr_minutes > now_minutes:
                        next_idx = i
                        break
                except (ValueError, AttributeError):
                    pass

        # Determine position state
        if last_departed == -1 and next_idx is None:
            # All stops are in the future - train hasn't started yet
            first_stop = stops[0]
            return PositionInfo(
                interpolated=False,
                between_stations=None,
                km_from_previous=None,
                minutes_ago=None,
                confidence="high",
                last_reported_stop=first_stop.code,
            )

        if last_departed == len(stops) - 1:
            # Train has reached destination
            last_stop = stops[-1]
            return PositionInfo(
                interpolated=False,
                between_stations=None,
                km_from_previous=None,
                minutes_ago=None,
                confidence="high",
                last_reported_stop=last_stop.code,
            )

        departed = stops[last_departed]
        next_stop = stops[next_idx]

        interpolated = True
        between_stations = f"{departed.code} → {next_stop.code}"
        km_from_previous = next_stop.distance_km if next_stop.distance_km else None

        # Calculate minutes since last reported stop
        last_actual_tm = departed.actual_departure_tm or departed.actual_arrival_tm
        minutes_ago = None
        if last_actual_tm:
            diff = (now_millis_val - last_actual_tm) / 60000
            minutes_ago = max(0, int(diff))
        elif departed.scheduled_departure:
            # Fall back to scheduled time if no actual time
            try:
                dep_parts = departed.scheduled_departure.split(":")
                dep_minutes = int(dep_parts[0]) * 60 + int(dep_parts[1])
                diff = now_minutes - dep_minutes
                if diff < 0:
                    diff += 24 * 60
                minutes_ago = max(0, diff)
            except (ValueError, AttributeError):
                pass

        confidence = "high" if minutes_ago is not None and minutes_ago < 15 else \
                    "medium" if minutes_ago is not None and minutes_ago < 45 else "low"

        return PositionInfo(
            interpolated=interpolated,
            between_stations=between_stations,
            km_from_previous=str(km_from_previous) if km_from_previous else None,
            minutes_ago=minutes_ago,
            confidence=confidence,
            last_reported_stop=departed.code,
        )

    def _build_contributions(self, results: dict, now_millis_val: int) -> List[SourceContribution]:
        """Build per-source contribution summary"""
        contributions = []
        for source_name, result in results.items():
            if result and result.stops:
                contributions.append(SourceContribution(
                    source=source_name,
                    fetched_at_millis=now_millis_val,
                    stops_covered=len(result.stops),
                    has_actuals=any(s.actual_arrival or s.actual_departure for s in result.stops),
                ))
        return contributions

    def _get_winner_label(self, results: dict) -> str:
        """Get label for winning source"""
        priority = ["ntes", "wimt", "railradar.in", "runningstatus.in", "erail.in"]
        label_map = {
            "ntes": "NTES live running status",
            "wimt": "WIMT live running status",
            "railradar.in": "RailRadar.in live running status",
            "runningstatus.in": "RunningStatus.in live running status",
            "erail.in": "erail.in live running status",
        }
        for source_name in priority:
            if source_name in results and results[source_name]:
                return label_map.get(source_name, f"{source_name} live running status")
        return "no source answered"

    def search_trains(self, from_station: str, to_station: str) -> List[dict]:
        """Search for trains between stations using erail.in"""
        erail = ErailSource()
        try:
            listings = erail.fetch_pair_search(from_station, to_station)
            return [tl.__dict__ for tl in listings]
        except Exception as e:
            return []


# Global aggregator instance
aggregator = FirstWinsAggregator()


def get_live_status(train_number: str) -> dict:
    """Get live status for a train - main entry point"""
    result = aggregator.merge(train_number)
    return {
        "train_no": result.train_no,
        "current_status": result.current_status,
        "current_station_name": result.current_station_name,
        "stops": [
            {
                "code": s.code,
                "name": s.name,
                "scheduled_arrival": s.scheduled_arrival,
                "scheduled_departure": s.scheduled_departure,
                "actual_arrival": s.actual_arrival,
                "actual_departure": s.actual_departure,
                "arrival_delay_minutes": s.arrival_delay_minutes,
                "departure_delay_minutes": s.departure_delay_minutes,
                "platform": s.platform,
                "distance_km": s.distance_km,
                "halt_minutes": s.halt_minutes,
                "is_current_station": s.is_current_station,
                "is_departed_here": s.is_departed_here,
                "is_next_station": s.is_next_station,
                "confidence": s.confidence,
                "source_provenance": s.source_provenance,
                "km_to_next": s.km_to_next,
                "expected_departure": s.expected_departure,
                "actual_arrival_tm": s.actual_arrival_tm,
                "actual_departure_tm": s.actual_departure_tm,
            }
            for s in result.stops
        ],
        "position": {
            "interpolated": result.position.interpolated if result.position else False,
            "between_stations": result.position.between_stations if result.position else None,
            "km_from_previous": result.position.km_from_previous if result.position else None,
            "minutes_ago": result.position.minutes_ago if result.position else None,
            "confidence": result.position.confidence if result.position else "low",
            "last_reported_stop": result.position.last_reported_stop if result.position else None,
        } if result.position else None,
        "source_label": result.source_label,
        "source_contributions": [
            {
                "source": c.source,
                "fetched_at_millis": c.fetched_at_millis,
                "stops_covered": c.stops_covered,
                "has_actuals": c.has_actuals,
            }
            for c in result.source_contributions
        ],
        "response_time_ms": result.response_time_ms,
        "fetched_at_millis": result.fetched_at_millis,
        "data_freshness": compute_data_freshness(result.fetched_at_millis),
    }


def search_trains(from_station: str, to_station: str) -> list:
    """Search for trains between stations"""
    return aggregator.search_trains(from_station, to_station)
