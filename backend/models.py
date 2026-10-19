"""
OpenTrack India - Data Models

Single source of truth for all data structures used across the backend.
All sources convert their data TO this format; all consumers read FROM this format.
"""

from dataclasses import dataclass, field, asdict
from typing import Optional, List, Dict, Any
from datetime import datetime, timezone
from enum import Enum


class SourceName(str, Enum):
    NTES = "ntes"
    WIMT = "wimt"
    RAILYATRI = "railyatri"
    RAILRADAR = "railradar"
    RUNNINGSTATUS = "railrunningstatus"
    ERAIL = "erail"
    CONFIRMTKT = "confirmtkt"


class DataFreshness(str, Enum):
    FRESH = "fresh"          # < 5 min
    STALE = "stale"          # 5-30 min
    STALE_OLD = "stale_old"  # 30+ min
    UNKNOWN = "unknown"


class StopStatus(str, Enum):
    SCHEDULED = "scheduled"
    ARRIVED = "arrived"
    DEPARTED = "departed"
    CANCELLED = "cancelled"
    UNKNOWN = "unknown"


@dataclass
class StopStatus:
    """Single stop in train route with actuals"""
    code: str                    # Station code (e.g., "MYS")
    name: str                    # Station name (e.g., "Mysore Jn")
    scheduled_arrival: Optional[str] = None   # HH:MM
    scheduled_departure: Optional[str] = None # HH:MM
    actual_arrival: Optional[str] = None      # HH:MM
    actual_departure: Optional[str] = None    # HH:MM
    arrival_delay_minutes: Optional[int] = None
    departure_delay_minutes: Optional[int] = None
    platform: Optional[str] = None
    distance_km: Optional[float] = None
    halt_minutes: Optional[int] = None
    is_current_station: bool = False
    is_departed_here: bool = False
    is_next_station: bool = False
    is_current_position: bool = False
    position_confidence: str = "low"  # high, medium, low
    source_provenance: str = "schedule"  # Which source provided actuals
    km_to_next: Optional[str] = None
    expected_departure: Optional[str] = None  # For stops without actuals
    # Unix timestamp fields for precise freshness tracking
    actual_arrival_tm: Optional[int] = None
    actual_departure_tm: Optional[int] = None


@dataclass
class PositionInfo:
    """Current position of train"""
    is_departed_from_origin: bool = False
    is_at_station: bool = False
    is_between_stations: bool = False
    current_station_code: Optional[str] = None
    current_station_name: Optional[str] = None
    between_station_from: Optional[str] = None
    between_station_to: Optional[str] = None
    minutes_since_last_report: Optional[int] = None
    km_from_last_station: Optional[float] = None
    confidence: str = "low"  # high, medium, low
    last_reported_stop: Optional[str] = None


@dataclass
class SourceContribution:
    """Track which source provided what data"""
    source: str
    fetched_at_millis: int
    stops_covered: int
    has_actuals: bool


@dataclass
class TrainStatus:
    """Complete train status - merged from all sources"""
    train_number: str
    train_name: str
    source_station_code: str
    source_station_name: str
    dest_station_code: str
    dest_station_name: str
    current_status: str  # "no position published", "running", "departed", etc.
    current_station_name: Optional[str] = None
    stops: List[StopStatus] = field(default_factory=list)
    position: Optional[PositionInfo] = None
    source_label: str = "unknown"
    source_contributions: List[SourceContribution] = field(default_factory=list)
    response_time_ms: int = 0
    fetched_at_millis: int = 0
    data_freshness: str = "unknown"  # fresh, stale, stale_old, unknown
    is_cached: bool = False
    cache_ttl_seconds: int = 60


@dataclass
class TrainListing:
    """Train search result"""
    train_number: str
    train_name: str
    source_station_code: str
    source_station_name: str
    dest_station_code: str
    dest_station_name: str
    scheduled_departure: Optional[str] = None
    scheduled_arrival: Optional[str] = None
    days_of_week: List[str] = field(default_factory=list)  # ["MON", "TUE", ...]
    runs_today: bool = False
    source: str = "erail"  # Which source provided this listing
    provenance: str = "schedule"


def now_millis() -> int:
    return int(datetime.now(timezone.utc).timestamp() * 1000)


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def compute_data_freshness(fetched_at_millis: int) -> str:
    """Determine if data is fresh, stale, or stale_old"""
    age_ms = now_millis() - fetched_at_millis
    age_min = age_ms / 60000
    if age_min < 5:
        return "fresh"
    elif age_min < 30:
        return "stale"
    elif age_min < 120:
        return "stale_old"
    else:
        return "unknown"


def compute_position_freshness(minutes_since_last_report: Optional[int]) -> str:
    """Determine position confidence based on age"""
    if minutes_since_last_report is None:
        return "low"
    if minutes_since_last_report < 5:
        return "high"
    elif minutes_since_last_report < 15:
        return "medium"
    else:
        return "low"


def stop_to_dict(stop: StopStatus) -> dict:
    return asdict(stop)


def position_to_dict(pos: PositionInfo) -> dict:
    return asdict(pos)


def train_status_to_dict(ts: TrainStatus) -> dict:
    return {
        "train_number": ts.train_number,
        "train_name": ts.train_name,
        "source_station_code": ts.source_station_code,
        "source_station_name": ts.source_station_name,
        "dest_station_code": ts.dest_station_code,
        "dest_station_name": ts.dest_station_name,
        "current_status": ts.current_status,
        "current_station_name": ts.current_station_name,
        "stops": [stop_to_dict(s) for s in ts.stops],
        "position": position_to_dict(ts.position) if ts.position else None,
        "source_label": ts.source_label,
        "source_contributions": [asdict(c) for c in ts.source_contributions],
        "response_time_ms": ts.response_time_ms,
        "fetched_at_millis": ts.fetched_at_millis,
        "data_freshness": ts.data_freshness,
        "is_cached": ts.is_cached,
        "cache_ttl_seconds": ts.cache_ttl_seconds,
    }


def train_listing_to_dict(tl: TrainListing) -> dict:
    return asdict(tl)
