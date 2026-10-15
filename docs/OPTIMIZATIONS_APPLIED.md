# OPTIMIZATIONS_APPLIED.md

## Optimizations
- Added per-source in-memory TTL cache (60s) for ConfirmTkt, RailYatri, ixigo (reduces redundant requests, matches existing NTES pattern)
- Enhanced sources to try multiple candidate endpoints + multiple header sets (mimics real app traffic)
- Kept parsers shape-guarded; flags remain false until validated with real captured data

## Feasibility
Build requires JDK/Android SDK (not present in this env). Kotlin sources compile structurally; no syntax issues detected. To test fetching from sources, need to either:
1. Run instrumented/unit tests via CI (gradle) - can trigger workflow with bg checker
2. Test with injected transport in unit tests (mocked HTTP)
3. Device capture for real endpoints
