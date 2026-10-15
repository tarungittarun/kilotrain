# CANDIDATE_DEEP_DIVE.md — Alternative sources to NTES (2026-10-15T11:28:27Z)

## Approach
Focus: pick 2 sources (excluding NTES, excluding WhereIsMyTrain) and implement in a way that can fetch **actual times** (stop-level actual arrival/departure), **actual position at that moment** (as reported by source), and **freshness** (age of data). 
Constraints: capture-first, no parser without capture; transport-injected; feature-flagged; typed failures; provenance via label(); first-wins ladder.

## Candidates assessed (web probes)
From naive probes (browser + Dalvik UA, Referer/Origin/X-Requested-With where plausible), all return mostly HTML dynamically; no obvious stable public JSON endpoints surfaced. 
Need device XHR capture (Android Studio Network Inspector) on owner device exercising:
- Search: train number (select by number) / pair (from→to) if exists
- Select: view route/stops
- Track: live running status refreshes

## Target 2 sources
Choose based on: aggregator that may show fresher actuals than NTES (crowdsourced) vs operator-aggregated. 
Preliminary candidates: **ConfirmTkt**, **ixigo**, **RailYatri**. All blocked-by-shape in sandbox.

### 1) ConfirmTkt (confirmtkt.com)
- Flows: train running status by train number (+ date). UI says "no login for tracking". 
- Web: pages are server-rendered HTML; AJAX endpoints not obvious from naive GETs (api_running returned 404 JSON). 
- Needs: device XHRs (likely POST/GET to internal API with headers). 
- Track fields to capture: current station, last updated/age, per-stop ETA/ETD/actual arr/dep (if present), delay, platform, position marker.
- Failures: 429/throttle, HTML error, empty, shape change.

### 2) RailYatri (railyatri.in)
- Mixes crowdsourced (passenger telemetry) + operator data. Potentially fresher for "actual position" vs NTES lag. 
- Mobile paths: m/live-train-status returned 200 HTML; API paths need headers (403 seen earlier in some cases). 
- Needs device XHRs. Track fields: last location update, current station, next station, per-stop actuals, "minutes late"/"on time", age/freshness.
- Failures: Akamai/edgesuite blocks, 429, anti-bot, shape varies.

## Implementation plan (capture-first)
1. Device capture (owner device): exercise each flow for same train (e.g. 56269 or real case) multiple times; save XHRs.
2. For each captured endpoint: commit redacted fixture + signature (`.sig.json`) under `poc/fixtures/cands_{vendor}/flow_*.txt`.
3. Create `core/src/main/kotlin/io/opentrack/core/{vendor}/` parser (pure JVM) against captured shape only; reject unknown shapes (SHAPE_UNKNOWN).
4. Create `app/src/main/kotlin/io/opentrack/app/source/{Vendor}RunningStatusSource.kt` with injected transport, timeouts (8/12s), retry policy per rules, typed failures.
5. Add `FeatureFlags.{VENDOR}_LIVE` (default off until validated), wire into `SourceLocator`, extend ladder in `ResultsEngine` as fallback behind NTES (first-wins). 
6. Provenance: include source label + `fetchedAtMillis` + age/freshness from feed if present; mark crowdsourced if RailYatri.
7. Tests: parser tests against fixtures; engine tests (first-wins, fallback on failure, no fabrication).

## Minimal schema to target (what we need to display)
- `trainNo`, `currentStationCode/Name`, `nextStationName`, `lastUpdate`/`age`, `platform`
- Per-stop: `stationCode/Name`, `schArr/schDep`, `actArr/actDep` (actuals), `delayMin`, `distanceKm`, `haltMin`, `isCurrent`
- Position: current/last reported stop + next; freshness (e.g. "updated X min ago" or feed stamp)

This is capture-first: until we have real XHRs + JSON, we cannot write parsers. The above probes confirm HTML-first; device capture is required.
