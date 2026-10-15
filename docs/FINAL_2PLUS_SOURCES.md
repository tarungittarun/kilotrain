# FINAL_2PLUS_SOURCES.md — ConfirmTkt, RailYatri, ixigo (3 sources)

## Work done
- ConfirmTkt, RailYatri, ixigo: core models+parsers + app sources with injected transport, typed failures, shape-guarded (return null -> SHAPE_UNKNOWN)
- Flags: CONFIRMTKT_LIVE=false, RAILYATRI_LIVE=false, IXIGO_LIVE=false
- SourceLocator wired; capture dirs created; deep attempts (3-4 per source) done, documented
- Focus: actual times, actual position, freshness for each

## Attempts (3-4 each)
1. Common REST paths + mobile headers
2. App-like UA + path variants  
3. Different hosts (api/mobile) + POST
4. Query param variants, mobile web paths

All HTML-first; no stable public JSON from naive probes. Device XHR capture needed.

## Ready
Scaffolds ready to consume real captured JSON. Enable only after capture + parser validation.
