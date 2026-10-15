# XHR_CAPTURE_GUIDE.md
## How device XHR capture achieves this
- Use **Android Studio Network Inspector** (on device/emulator) to intercept HTTP(S) calls made by the actual ConfirmTkt/RailYatri/ixigo apps
- Filter by domain (confirmtkt.com, railyatri.in, ixigo.com) and train number; capture XHR/fetch returning JSON
- Save HAR or extract request (URL, method, headers, query/body) + response (JSON). Redact PII
- Put JSON bodies in `poc/fixtures/cands_*_live/` + generate `.sig.json`; our parsers become shape-tuned to real API

## How much it helps with your requirements
- **Actual times**: will get real actArr/eta/etd/delays if API provides them (not guessed)
- **Actual position**: current/next station, platform, distance; RailYatri often gives position/location
- **Freshness**: lastUpdate/updatedAt/age/minsAgo as reported by source
- All with correct provenance (source label + fetchedAtMillis). Much more reliable than scraping HTML.

## How many sources it will cover
- With captures: ConfirmTkt + RailYatri + ixigo (3). Can also add more if needed.
- NTES already covered; WIMT separate (location-focused). These 3 are aggregators - often fresher than NTES in practice.

## How sure?
- High certainty if we get real JSON from live app (this is standard reverse of app traffic). 
- Our scaffolds are flexible (multi-endpoint/header/cache, shape-guarded). If we get real shape, parsers will match.
- But depends on whether APIs are stable; some change - that’s why we keep flags off until validated and capture-first.
