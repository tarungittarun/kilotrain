"""
OpenTrack India - API Layer

Flask-based REST API that serves the multi-source train tracking backend
to the frontend. All data comes from the aggregator which merges
NTES, WIMT, RailRadar.in, RunningStatus.in, and erail.in.

Endpoints:
- GET /api/v1/train/{train_number} - Live status with per-stop actuals
- GET /api/v1/search?from=XXX&to=YYY - Train list between stations
- GET /api/v1/train/{train_number}/timetable - Timetable with actuals
- GET /api/v1/sources/status - Source health status
- GET /api/v1/health - API health check
"""

from flask import Flask, jsonify, request
from flask_cors import CORS
import logging
import sys
import os

# Add backend to path
sys.path.insert(0, os.path.dirname(__file__))

from aggregator import get_live_status, search_trains, aggregator
from models import train_status_to_dict, train_listing_to_dict

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Create Flask app
app = Flask(__name__)
CORS(app)  # Enable CORS for frontend

@app.route('/api/v1/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        "status": "healthy",
        "timestamp": __import__('time').time() * 1000,
        "sources": {
            "WIMT": aggregator.sources[1].is_healthy if len(aggregator.sources) > 1 else False,
            "RailRadar.in": aggregator.sources[2].is_healthy if len(aggregator.sources) > 2 else False,
            "RunningStatus.in": aggregator.sources[3].is_healthy if len(aggregator.sources) > 3 else False,
            "erail.in": aggregator.sources[4].is_healthy if len(aggregator.sources) > 4 else False,
            "NTES": aggregator.sources[0].is_healthy if len(aggregator.sources) > 0 else False,
        }
    })


@app.route('/api/v1/train/<train_number>', methods=['GET'])
def get_live_status(train_number):
    """
    Get live running status for a train.
    
    Returns per-stop actual vs scheduled times, delays, platform,
    position pinpointing, and data freshness info.
    """
    try:
        result = get_live_status(train_number)
        return jsonify({
            "success": True,
            "data": result
        })
    except Exception as e:
        logger.error(f"Error fetching live status for {train_number}: {e}")
        return jsonify({
            "success": False,
            "error": str(e),
            "train_number": train_number
        }), 500


@app.route('/api/v1/train/<train_number>/timetable', methods=['GET'])
def get_timetable(train_number):
    """
    Get timetable for a train with all stops and actuals.
    Same data as live status but focused on timetable view.
    """
    try:
        result = get_live_status(train_number)
        return jsonify({
            "success": True,
            "data": {
                "train_number": result["train_no"],
                "train_name": result.get("train_name", ""),
                "source_station": {
                    "code": result.get("source_station_code", ""),
                    "name": result.get("source_station_name", ""),
                },
                "dest_station": {
                    "code": result.get("dest_station_code", ""),
                    "name": result.get("dest_station_name", ""),
                },
                "stops": result.get("stops", []),
                "position": result.get("position"),
                "source_label": result.get("source_label", ""),
                "data_freshness": result.get("data_freshness", "unknown"),
                "fetched_at_millis": result.get("fetched_at_millis", 0),
            }
        })
    except Exception as e:
        logger.error(f"Error fetching timetable for {train_number}: {e}")
        return jsonify({
            "success": False,
            "error": str(e),
        }), 500


@app.route('/api/v1/search', methods=['GET'])
def search_trains():
    """
    Search for trains between stations.
    
    Query params:
    - from: Source station code (e.g., MYS)
    - to: Destination station code (e.g., SBC)
    """
    from_station = request.args.get('from', '')
    to_station = request.args.get('to', '')
    
    if not from_station or not to_station:
        return jsonify({
            "success": False,
            "error": "Missing 'from' or 'to' parameter"
        }), 400
    
    try:
        results = search_trains(from_station, to_station)
        return jsonify({
            "success": True,
            "from": from_station,
            "to": to_station,
            "count": len(results),
            "trains": results
        })
    except Exception as e:
        logger.error(f"Error searching trains {from_station}->{to_station}: {e}")
        return jsonify({
            "success": False,
            "error": str(e),
        }), 500


@app.route('/api/v1/sources/status', methods=['GET'])
def sources_status():
    """Get health status of all data sources"""
    statuses = {}
    for source in aggregator.sources:
        statuses[source.name] = {
            "healthy": source.is_healthy,
            "consecutive_failures": source.consecutive_failures,
            "total_requests": source.total_requests,
            "successful_requests": source.successful_requests,
            "avg_response_ms": round(source.avg_response_ms, 2),
        }
    
    return jsonify({
        "success": True,
        "sources": statuses,
        "timestamp": now_millis() if 'now_millis' in dir() else 0,
    })


@app.route('/api/v1/sources/refresh', methods=['POST'])
def refresh_sources():
    """Force health check on all sources"""
    for source in aggregator.sources:
        try:
            source.health_check()
        except Exception:
            source.record_failure()
    
    return jsonify({
        "success": True,
        "sources": {
            s.name: {
                "healthy": s.is_healthy,
                "consecutive_failures": s.consecutive_failures,
            }
            for s in aggregator.sources
        }
    })


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
