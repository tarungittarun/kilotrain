# Implementation Plan: 2 Alternative Sources for Actual Times + Position + Freshness (90 min focused)

## Selection
Pick **2 sources** (excluding NTES, excluding WhereIsMyTrain). From assessment:
1. **ConfirmTkt** (confirmtkt.com) - aggregator; may have fresher actuals in some cases; needs device XHRs for internal API
2. **RailYatri** (railyatri.in) - crowdsourced + operator; strong candidate for "actual position at that moment"; needs device XHRs

Both are blocked-by-shape in naive probes (HTML-first). Goal: implement minimal, capture-first sources that can surface actual times, actual position, freshness.

## Requirements to satisfy
- Fetch **actual times** at station level (actArr/actDep) if source reports them
- Fetch **actual position at that moment** (current/last reported stop + next station + distance if any)
- Fetch **how fresh the data is** (feed stamp / "updated X min ago" / last update timestamp)

## Plan (90 min)

### 0. Setup (0-10 min)
- [ ] Read existing source pattern: `NtesRunningStatusSource`, `ErailTrainSource`, `ResultsEngine`, `SourceLocator`
- [ ] Read core NTES parser/model to mirror structure
- [ ] Create directories: `core/src/main/kotlin/io/opentrack/core/confirmtkt/`, `core/src/main/kotlin/io/opentrack/core/railyatri/`, and corresponding app sources

### 1. Capture-first: device XHRs needed (10-25 min)
Since web probes gave HTML, we must capture real XHRs from Android app:
- For ConfirmTkt app/web: exercise live running status by train number (e.g. 56269) → capture endpoint, method, query/body, headers, JSON response
- For RailYatri: same → capture XHR that returns live status JSON (actual times, current position, freshness)
- Save redacted fixtures: `poc/fixtures/cands_confirmtkt_live/*.txt` + `.sig.json`, `poc/fixtures/cands_railyatri_live/*.txt` + `.sig.json`

If not available now, document exact capture steps in report. But proceed to scaffold code assuming captured shape.

### 2. Core models + parsers (25-45 min)
**ConfirmTkt (core/confirmtkt/)**
- [ ] `ConfirmTktStop.kt` / data classes: stop with sch/act times, delay, platform, isCurrent
- [ ] `ConfirmTktRunningStatus.kt`: trainNo, current station, next station, lastUpdate/freshness, position info, stops, fetchedAtMillis
- [ ] `ConfirmTktParser.kt`: pure JVM, parse captured JSON only; return null on shape mismatch (SHAPE_UNKNOWN). Extract actual times + position + freshness.

**RailYatri (core/railyatri/)**
- [ ] `RailYatriStop.kt`, `RailYatriRunningStatus.kt` with similar fields
- [ ] `RailYatriParser.kt`: handle crowdsourced fields; extract actuals, current position, last update/freshness

### 3. App sources (injected transport) (45-60 min)
**app/src/main/kotlin/io/opentrack/app/source/ConfirmTktRunningStatusSource.kt**
- [ ] Inject `openConnection` or `HttpTransport` style (match existing). Timeouts 8/12s
- [ ] Build request to captured endpoint with required headers (UA/Referer/Origin/X-Requested-With if needed)
- [ ] Parse via core parser; map to typed failures (OFFLINE/TIMEOUT/HTTP/EMPTY/SHAPE_UNKNOWN)
- [ ] Include freshness/age in diagnostics

**RailYatriRunningStatusSource.kt** similar, with provenance noting crowdsourced if applicable.

### 4. Wire into ladder + flags (60-75 min)
- [ ] Add `FeatureFlags.CONFIRMTKT_LIVE`, `FeatureFlags.RAILYATRI_LIVE` (default false until validated)
- [ ] Add to `SourceLocator` (confirmTktStatusSource, railYatriStatusSource)
- [ ] Extend `ResultsEngine`: when looking up live status (train number), try sources behind flags in order (e.g. NTES first if available, then ConfirmTkt, then RailYatri) - first-wins. Pass through actual times/position/freshness to UI.
- [ ] Log wire signatures on failure.

### 5. Tests + validation (75-90 min)
- [ ] Unit tests: parser tests against captured fixtures (happy path + missing fields)
- [ ] Engine test: ConfirmTkt returns fresher actuals and is chosen if NTES lags? Or ladder order configurable
- [ ] Lint/typecheck if possible (or document what couldn’t run)
- [ ] Write progress report with checklist results, fixtures found, scaffolding done, next steps for device capture

## Notes
- No fabrication: if actual time missing, leave null (em dash). 
- Freshness: always carry `fetchedAtMillis` + source-reported last update; surface age to UI/diagnostics.
- Crowdsourced (RailYatri): provenance label must reflect nature.
- Capture-first: if no captured JSON exists yet, scaffolds still written but marked BLOCKED until capture added.
