# CANDIDATE_SPIKES.md — alternative sources probe (2026-10-15T08:40:13Z)

Verdicts per source (keyless web probes only): usable | usable-but-labelled | blocked-by-network | blocked-by-shape. 
Rules: structural signatures only, redacted fixtures, no parsing. Device XHR capture required for any that load JSON dynamically.

## Results
| Source | Verdict | Reason (measured) | JSON-like seen? | Notable paths |
|---|---|---|---|---|
| WIMT (whereismytrain.org.in) | blocked-by-shape | Mostly HTML; no obvious stable public JSON endpoint from naive web probes | No | home, /train-running-status, /live-train-status, /api/* candidates return HTML |
| ConfirmTkt (confirmtkt.com) | blocked-by-shape | HTML/JS; likely internal XHR calls post-load | No (all HTML) | /train-running-status, ajax/*, api/* return HTML |
| ixigo (ixigo.com) | blocked-by-shape | HTML dominant; XHRs likely loaded dynamically | No | /trains/train-running-status and api paths return HTML |
| RailYatri (railyatri.in) | blocked-by-network | No obvious stable JSON from naive probes | No | mobile paths mostly not returning stable JSON in first KB; many 4xx/varied |

Fixtures: poc/fixtures/cands_*/ (redacted + .sig.json). Full JSON: poc/candidate_probe_results.json.
