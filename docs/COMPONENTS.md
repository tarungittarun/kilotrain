# COMPONENTS.md — PoC register (KICKOFF §7.1). One file, one question, one command, PASS/FAIL/BLOCKED.

| component | question | command | verdict | artefact |
|---|---|---|---|---|
| probe_sources | which sources answer, from where, with what shape? | `python3 poc/probe_sources.py` | PASS (measured) — see docs/spike-verdicts.md | poc/probe-results.json + poc/fixtures/ |
| s1 reachability | does the NTES host answer from this environment? | probe samples S1 | BLOCKED (no envelope capture; intermittent reachability 3/3, one 19.7s outlier) | poc/fixtures/s1_ntes/appservand_reachability.txt |
| s2 reachability + shape | does erail return trains-between-stations? | probe candidates S2 | BLOCKED (200s are throttle control messages; no rows, no feed stamp) | poc/fixtures/s2_erail/*.txt + .sig.json |
| s3 vintage test | does a fresh-looking repo contain fresh data? | probe S3 | PASS as a GATE (caught 044078 in a repo updated today) | findings in probe-results.json |
| s4 open-data | is data.gov.in admissible? | probe S4 | BLOCKED (cadence/vintage/key policy unmeasured; ADR + consent required) | probe-results.json |

Graduation rule (§7.2): every PASS row above became (or becomes) a real test/script in the repo;
no prototype survives in main. The probe itself is the capture mechanism (7.4) and stays in
`poc/` as a permanent tool.
