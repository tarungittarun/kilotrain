# M0 SPIKE VERDICTS — source reachability + shape, measured 2026-10-11 (sandbox)

One page, per KICKOFF §8/M0. Machine-readable run: `poc/probe-results.json` (artefact, gitignored).
Reproduce: `python3 poc/probe_sources.py`. Verdict vocabulary: usable | usable-but-labelled |
blocked-by-network | blocked-by-shape.

| id | verdict | what was measured (real output) | what this means |
|---|---|---|---|
| S1 NTES/CRIS | **blocked-by-shape** | `POST /crisns/AppServAnd` empty body: 3/3 reachable (0.165s, 0.364s, 19.718s); an earlier `GET` timed out after 20s. Bodies: empty | Host reachable from THIS sandbox, intermittently and slowly. NEW EVIDENCE vs the brief's "sandbox blocked" record (re-measured, recorded, not re-litigated). No envelope capture yet, so no parser (7.4). CI reachability UNMEASURED — the CI probe run decides the M2 branch |
| S2 erail.in | **blocked-by-shape** | both probed paths 200 in <0.25s, SAME body signature, body = `~~~~~Please try again after some time.` | Host alive, but every payload is a throttle/control message: no rows, no feed stamp. Parser blocked until a real data capture (7.4). Fixture: `poc/fixtures/s2_erail/` |
| S3 GitHub dumps | **usable-but-labelled** | `datameet/railways` (updatedAt 2026-10-11): first 2048 bytes of `trains.json` contain 3 hits of retired 0-prefix number `044078` | Cold-start fallback ONLY, permanently labelled. Proof of the recorded failure mode: the repo's commit date is TODAY and its CONTENT is still pre-renumbering |
| S4 open-data | **blocked-by-shape → UNADMITTED** | `data.gov.in` root 200 | Cadence, vintage and key policy UNMEASURED. Stays unadmitted until measured through the known-truths gate + ADR + owner consent |

## Phone-visible change
NONE. This is research, not progress (KICKOFF §14.5). SAY SO.

## What decides the next branch (M2) — MEASURED 2026-10-11, CI run 38175068398
The probe now runs in CI. The CI run's captured results answer the branch question that M0 left
open — read from artefact `probe-capture-38175068398`, not assumed:
- **S1 from GitHub runners: blocked-by-network, 0/3 samples, every one a ~20 s timeout.** The
  brief's "TLS dies from CI-hosted runners" record is CONFIRMED for S1.
- **S2 from CI: blocked-by-shape, identical to sandbox** (throttle control messages).

Therefore **M2 opens only with a captured payload from the owner's phone** (needs-device row
below), or with a new CI measurement. No parser is written before a capture exists (7.4).

## Gate discipline proven by this run
- The probe's first pass reported S2 "usable-but-labelled" because tildes were present — WRONG;
  the body was a throttle message. Verdict logic now requires rows + documented feed stamp.
  The lesson stands: a 200 with the right delimiter is not data. (`poc/fixtures/s2_erail/*.sig.json`)
- A single timeout is not a verdict: S1's GET timed out once, POST then answered 3/3 with one
  19.7s outlier. Verdicts quote sample counts, never one sample.
