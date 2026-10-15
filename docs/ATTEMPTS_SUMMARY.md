# ATTEMPTS_SUMMARY.md — ConfirmTkt, RailYatri, ixigo (excluding NTES, WIMT)

## Attempts made (3-4 per source)
1. Standard mobile headers (Dalvik/okhttp) + common REST paths → mostly 404/406/500; some return HTML
2. App-specific UA strings + path variants → HTML/404
3. Different hosts (api.*, mobile.*) + POST JSON → errors (404/connection issues)
4. Query param variants + mobile web paths → HTML

## Findings
- All return HTML-first for web/mobile endpoints in naive probes
- Guessed public JSON APIs not readily accessible without exact headers/session from real app
- Device XHR capture remains necessary (Android Studio Network Inspector / mitmproxy) on real app traffic

## Recommendation
Continue with capture-first scaffolds (already added). If device capture unavailable now, keep flags disabled and parsers shape-guarded. This matches project rules (no parser without capture, no fabrication).

## Additional sources to consider (other than mentioned)
Also check: Trainman, RailRestro-related APIs? But likely need keys. Stick to keyless where possible. 
Focus on ConfirmTkt + RailYatri + ixigo as requested; all need device capture.

## Additional: ixigo
Scaffolded (core+app) with flexible shape; probes show HTML-first, endpoints likely need device XHR capture. Flag IXIGO_LIVE=false.
