# DATA_SOURCES.md — §4 ledger, re-measured 2026-10-11

Verdicts come from `poc/probe_sources.py` (2026-10-11 runs; sandbox run 21:02 UTC, CI run
`38175068398` — measured FROM GitHub runners, artefact `probe-capture-38175068398`).
Full detail: docs/spike-verdicts.md.

| id | endpoint | auth | shape | cadence | verdict (measured) | notes |
|---|---|---|---|---|---|---|
| S1 | enquiry.indianrail.gov.in/crisns/AppServAnd (POST) | none (public envelope constants) | AES-CBC + MD5 framed (unknown without capture) | 5–10 min | sandbox: blocked-by-shape (host answered 3/3 POSTs, INTERMITTENT, no envelope capture) · CI: **blocked-by-network 0/3, all ~20 s timeouts** | Brief's "runners blocked" record CONFIRMED from CI; sandbox intermittence recorded, not re-litigated. CI probe decides M2's branch: no parser until a capture exists |
| S2 | erail.in/rail/getTrains.aspx | none | flat `~`-separated + feed stamp | hours | blocked-by-shape (host alive; 200s carry throttle control messages, same signature both paths) | Path matters: wrong path = 404 while host is alive. Keyless, second witness |
| S3 | datameet/railways trains.json @ e0c538a1 (pinned) | none | route GeoJSON, identity+times | repo commit date 2016-08-08 ≠ data vintage | **ADMITTED as labelled cold-start fallback (ADR 0007)**: 5,208 rows measured, 176 retired 0-prefix dropped, 84 incomplete dropped; 4,948 ship; 116 KB gzipped | build-time fetch only, never from the app; vintage printed on the artefact and on screen |
| S4 | data.gov.in / CRIS-NPRTI bulk | unknown, possibly free key | unknown | unknown | UNADMITTED, candidate | Measure cadence/vintage/key policy through known-truths gate; ADR + owner consent before any use |
| S5 | RailRadar / Railway Engine / indianrailapi.com / railwayapi.com / Apify | keys | — | — | forbidden as runtime providers | Design reference only (LIVE/RECENT/STALE envelope) |

Review clock: monthly re-measure, or immediately on a gate FAIL. Last measured: 2026-10-11.
