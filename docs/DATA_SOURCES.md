# DATA_SOURCES.md — §4 ledger, re-measured 2026-10-11

Verdicts come from `poc/probe_sources.py` (2026-10-11 run, sandbox). Full detail: docs/spike-verdicts.md.

| id | endpoint | auth | shape | cadence | verdict (measured) | notes |
|---|---|---|---|---|---|---|
| S1 | enquiry.indianrail.gov.in/crisns/AppServAnd (POST) | none (public envelope constants) | AES-CBC + MD5 framed (unknown without capture) | 5–10 min | blocked-by-network (sandbox + CI-hosted runners) | Brief: fine from a phone. Re-measure from device later (needs-device). No parser until a capture exists |
| S2 | erail.in/rail/getTrains.aspx | none | flat `~`-separated + feed stamp | hours | see spike-verdicts.md | Path matters: wrong path = 404 while host is alive. Keyless, second witness |
| S3 | GitHub timetable dumps | none | CSV/JSON dumps | repo commit dates ≠ data vintage | cold-start fallback only, permanently labelled | MEASURED FAILURE history: pre-2022-23 renumbering content (0-prefix numbers, reverse-keyed train) |
| S4 | data.gov.in / CRIS-NPRTI bulk | unknown, possibly free key | unknown | unknown | UNADMITTED, candidate | Measure cadence/vintage/key policy through known-truths gate; ADR + owner consent before any use |
| S5 | RailRadar / Railway Engine / indianrailapi.com / railwayapi.com / Apify | keys | — | — | forbidden as runtime providers | Design reference only (LIVE/RECENT/STALE envelope) |

Review clock: monthly re-measure, or immediately on a gate FAIL. Last measured: 2026-10-11.
