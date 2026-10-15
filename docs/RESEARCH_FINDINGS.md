# RESEARCH_FINDINGS.md

## Truth
Apps that show live data from aggregators (ConfirmTkt/RailYatri/ixigo) typically:
- Use internal/private API endpoints with different hosts, auth tokens, or signatures
- May be behind CDN with anti-bot + require specific headers/session
- Often change endpoints frequently; web probes alone don't reveal mobile API calls

## Attempts (exhaustive)
1-4 attempts per source with varied headers, hosts, methods, params - no stable JSON obtained in sandbox.

## Practical approach for this codebase
- Our scaffolds are flexible (multi-endpoint + multi-header + caching) and shape-guarded
- Flags remain false until real device-captured JSON is added
- Capture-first is the only reliable way given constraints

We need device XHR capture from actual apps to get the real endpoints.
