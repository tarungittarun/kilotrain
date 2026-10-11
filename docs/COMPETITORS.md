# COMPETITORS.md — quarterly review (KICKOFF §5, §13.7). First pass 2026-10-11.

Standing "what we refuse to copy": booking, PNR prediction, chat, social, ads, anything requiring
our own server or a licence.

| competitor | wins by | evidence (re-check, not re-litigate) | transferable under §3 | we do NOT copy |
|---|---|---|---|---|
| Where Is My Train | sensing + caching; GPS/cell-tower triangulation, crowdsensing, offline cached estimation | Wikipedia entry (Google-owned) | labelled caching, TTL discipline, honest "could not look" copy | its Google ownership/back-end dependence |
| RailYatri | freshness via crowdsourced GPS/cell from passengers' phones | RailYatri FAQ: live status is crowdsourced; railway's model lags on last-stop reports | the idea that user observations are real actuals (→ override store, M4) | its crowdsourcing of user data to a server |
| ixigo | IRCTC Authorised Partnership + caching + backend offline location | ixigo materials | offline caching patterns | the partnership/backend requirement |
| Railway Engine | honest freshness envelope: LIVE/RECENT/STALE + provider health | API docs | the freshness-envelope MODEL (§3.2 implements it) | the keyed dependency |
| official NTES app `cris.icms.ntes` | official data | Google Play listing updated Aug 2026 | proves the data we need is official and reachable | closed-source app |

Next review: 2027-01-11 (quarterly), or on a gate FAIL.
