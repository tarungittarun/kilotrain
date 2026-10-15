# ATTEMPTS_DETAILED.md - Final attempts to get JSON endpoints

## Approaches tried (all 3 sources)
1. Mobile browser headers (gzip/br) vs app UAs
2. POST with JSON bodies (various param names: trainNo, trainNumber, train_number, tn)
3. GET with query params + date
4. Different hosts (api.*, mobile.*), different paths (/api/v1/train/running-status, /services/trains/runningStatus, etc.)
5. Mimicking XHR with Referer/Origin/X-Requested-With
6. Scraping embedded JSON in HTML pages (__NEXT_DATA__, script tags) - minimal success
7. Form data vs JSON for POST
8. App package UAs (com.confirmtkt.android, com.railyatri.in, com.ixigo.train)

## Results
- Some endpoints return 404 with JSON error (RailYatri POSTs) - API exists but param/path wrong
- Most return HTML or 406/500
- No easily extractable stable JSON from public endpoints with naive calls
- Device XHR capture from real app is required to get exact endpoints/headers/params

## Practical fix: make sources more flexible
- Accept configurable endpoint (already have endpoint param)
- Try multiple candidate endpoints in order on failure
- Try both GET/POST with common param shapes
- Accept both JSON and form encoding
- Better header sets
