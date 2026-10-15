# CAPTURE_INSTRUCTIONS.md — Device XHR capture for ConfirmTkt & RailYatri

## Goal
Capture exact API calls used for "Live Train Running Status" (train number) to get actual times, actual position, freshness.

## Required captures (minimum)
For each source, capture for same train (e.g. 16228 or 12345):
1. Initial load of live running status screen
2. Refresh/reload (to see if different endpoint called)
3. Normal case + observe any errors

## ConfirmTkt (Android app or mobile web)
- App: ConfirmTkt - IRCTC Train Booking
- Exercise: Train Running Status → Enter train number → View live status
- Look for XHR/Fetch returning JSON with: stops/route, actual/eta/etd, current/next station, last update, delay, platform

Likely endpoints to watch: /api/*, /services/*, mobile API endpoints, sometimes POST with JSON. Headers may include X-Requested-With, Referer, Origin, app version.

## RailYatri (Android app)
- App: RailYatri - Train Status, PNR, Tickets
- Exercise: Live Train Status → Train number → View status (multiple refreshes)
- Look for JSON containing: current station, next station, position/location, lastUpdated/age, per-stop actuals, crowdsourced indicators

## How to capture (recommended)
1. **Android Studio Network Inspector** (best): Run app on device/emulator, open Network Inspector, filter by train number/domain, reproduce flows, save captured requests/responses (HAR). Export relevant JSON responses.
2. **mitmproxy**: Install mitmproxy cert on device, proxy traffic, save flows. Redact sensitive headers/cookies if any.

## Redaction rules (per project)
- Save only response bodies as fixtures (first reasonable bytes not needed if full JSON small; but capture full JSON structure)
- Remove PII; keep structure
- Create files: poc/fixtures/cands_confirmtkt_live/{flow}.json (or .txt) + .sig.json with structural signature
- Update parsers against real shapes after capture

## Once captured
- Place JSON in poc/fixtures/cands_confirmtkt_live/ and cands_railyatri_live/
- Generate structural signatures (we have sig logic in probes)
- Test parsers against fixtures
- Enable FeatureFlags (set to true) after validation
- Wire into ResultsEngine ladder (NTES → ConfirmTkt → RailYatri)
