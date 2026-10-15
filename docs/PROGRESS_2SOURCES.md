# Progress Report: 2 Alternative Sources (90 min)

## Time snapshot
Focus: ConfirmTkt + RailYatri (excluding NTES, WIMT). Capture-first. Goal: scaffolds to fetch actual times, actual position, freshness.

## 1) Probes (deep)
- Ran enhanced probes: confirmtkt/railyatri deep. Findings: mostly HTML-first; some 404 JSON for guessed API paths. No stable public JSON from naive GETs. 
- Fixtures written: poc/fixtures/cands_ct_deep/*, poc/fixtures/cands_ry_deep/*, plus earlier candidates.
- Verdicts unchanged (sandbox): blocked-by-shape - device XHR capture required.

## 2) Core models + parsers (scaffolded)
- core/confirmtkt/ConfirmTktRunningStatus.kt: stops + running status (actuals, delays, platform, current/next, lastUpdate, freshnessLabel, fetchedAtMillis)
- core/confirmtkt/ConfirmTktParser.kt: JSON reader-based, tries common keys (stops/STNS/route, schArr/sta etc.), returns null on shape mismatch (SHAPE_UNKNOWN)
- core/railyatri/RailYatriRunningStatus.kt: includes isCrowdsourced=true, position, isPassed
- core/railyatri/RailYatriParser.kt: flexible field names, extracts freshness/position/actuals; returns null if no stops

## 3) App sources (transport-injected)
- app/source/ConfirmTktRunningStatusSource.kt: GET with UA/Referer/Origin/X-Requested-With, timeouts 8/12s, typed failures (OFFLINE/TIMEOUT/HTTP/EMPTY/SHAPE_UNKNOWN), injects openConnection
- app/source/RailYatriRunningStatusSource.kt: similar, crowdsourced noted, endpoint configurable

## 4) Integration hooks
- FeatureFlags: CONFIRMTKT_LIVE=false, RAILYATRI_LIVE=false (opt-in until validated)
- SourceLocator: confirmTktStatusSource, railYatriStatusSource registered

## 5) What they fetch (target fields)
- Actual times: actArr/actDep (and delays). If missing, left null → UI renders em dash (honest).
- Actual position: current station, next station, isCurrent, distanceKm, position (RailYatri). Also platform.
- Freshness: lastUpdate/freshnessLabel/age/minsAgo + fetchedAtMillis (always carried). Crowdsourced explicitly flagged for RailYatri.

## 6) Device capture requirement (critical)
No usable JSON from probes. To actually fetch real data, need XHR capture from Android app:
- ConfirmTkt: train running status by trainNo (maybe with date). Likely internal API returns JSON with stops + actuals.
- RailYatri: mobile live status by trainNo; returns JSON with current position + actuals + last update.

## Checklist completion (90 min scope)
- [x] Setup/read patterns
- [x] Deep probe run + fixtures
- [x] Core models+parsers scaffolded (capture-first, shape-guarded)
- [x] App sources with injected transport + typed failures
- [x] FeatureFlags + SourceLocator wired
- [x] Docs (deep dive + progress) written
- [ ] Device XHR capture → real JSON fixtures (required before enabling flags)
- [ ] Parser tests against captured JSON
- [ ] Wire into ResultsEngine ladder (optional but needed to use in UI)

## Recommendation
Scaffolds are ready. Enable only after capturing real JSON from device (capture-first). Order: NTES → ConfirmTkt → RailYatri behind flags (first-wins). Both sources implemented to surface actual times, position, freshness with provenance.
