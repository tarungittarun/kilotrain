# ALTERNATIVE_APPROACHES.md — Getting actual times/position/freshness in mobile app

## Reality check
Naive web probes mostly return HTML; guessed REST paths 404. Real apps likely use:
- App-specific API hosts (api.confirmtkt.com, mobile APIs, or CDN-backed APIs)
- Require headers: User-Agent (app), X-Requested-With, Accept, Referer/Origin, sometimes auth tokens, device-id, app-version
- May use POST with JSON body or encrypted params
- Often behind Akamai/CDN with bot protection

## Feasible approaches for this app (keyless, transport-injected, honest)
1. **Device traffic capture (Network Inspector / mitmproxy)** - capture exact XHRs from ConfirmTkt/RailYatri Android apps while viewing live running status for a train. This gives precise endpoints, params, headers, JSON shape. Highest fidelity. (matches repo’s capture-first rule)
2. **Reverse-engineer from public web JS** - fetch page HTML + bundled JS, look for API calls; can reveal endpoints. Less reliable as they minify/change.
3. **Use existing public-ish endpoints** - some sites expose JSON if correct headers passed; but need exact shape. Rare from probes.
4. **Community APIs / scraping mirrors** - but we must avoid keys where not needed and stay keyless; also need stability + honesty.
5. **Alternative official-ish sources** - RAILAPI type services require keys (forbidden as runtime providers per design). Avoid.
6. **Leverage erail.in more** - erail has train route/times; “actual” position may be limited. But we already have erail rung.

## Recommended concrete actions (to actually get data)
- **Primary: Device capture**. Use Android Studio Network Inspector on device/emulator running ConfirmTkt and RailYatri apps. Open live running status for train (e.g. 12345/16228), refresh multiple times. Save HAR/captured JSON. Extract: URL, method, headers, query/body, response. Redact PII.
- **Secondary: Headless capture of dynamic pages** - use tools like curl+puppeteer not directly here, but we can try with realistic headers + follow redirects. Some sites return initial HTML that loads JSON via XHR; we only see initial HTML in simple GET.
- **Focus on 2 sources first**: ConfirmTkt + RailYatri. For each, capture the “live running status” XHR that contains per-stop actuals, current position, last update/freshness.

## Implementation plan (pragmatic)
1. Create `poc/capture_instructions.md` with exact steps for device capture.
2. Once we get real JSON fixtures (from device), add to `poc/fixtures/cands_{vendor}_live/` with signatures.
3. Update parsers to match real shapes (they’re flexible now; tighten after seeing real).
4. Wire into ResultsEngine ladder: NTES (if available) → ConfirmTkt (if flag true and valid) → RailYatri (if flag true) as fallbacks (first-wins). Surface actual times/position/freshness.
5. Feature flags stay false until validated against real captured data.

## Key fields to extract from captured JSON
- actualArrival/actualDeparture (or ETA/ETD), delay minutes
- current station / next station / current position
- last updated timestamp / age (freshness)
- per-stop list with isCurrent/passed

Device capture is the only reliable path given probes return HTML. The scaffolds we added are ready to consume real captured JSON.
