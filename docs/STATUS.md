# STATUS.md — the tracker (KICKOFF §13.3). One machine-readable table.

Resume rule: first DOING row, else first NEXT. DONE requires an artefact NAME. One open
owner-action only. Any behaviour-changing commit must change this table (docs-freshness gate).

| id | task | state | verified-by | next action | needs-device | owner-action |
|---|---|---|---|---|---|---|
| M0-1 | docs skeleton (§13.1) committed | DONE:2026-10-11 | git history, first commit | none | no | none |
| M0-2 | capture tool + source probe (§15.3) | DONE:2026-10-11 | `python3 poc/probe_sources.py` → docs/spike-verdicts.md + poc/fixtures/ | weekly CI probe will re-measure S1/S2 from runners | no | none |
| M0-3 | probe wired into CI with capture artefacts | DONE:2026-10-11 | .github/workflows/checks.yml captures poc/fixtures + probe-results.json | read first CI run verdicts | no | none |
| S1-* | story files for every screen and state | NEXT | docs/stories/ exists, owner read | write stories/M1-*.md from the template | no | none |
| M1-1 | contract types + provenance + label() (pure JVM) | NEXT | :core:test green in CI run | implement core module | no | none |
| M1-2 | known-truths gate run by the data build (§10.7) | NEXT | scripts/known_truths_gate.py output in CI + verdict in artefact | script + data/known_truths.json | no | none |
| M1-3 | labelled states on the results screen | NEXT | screen tests assert exact copy per state | app module + Robolectric tests | no | none |
| M1-4 | SQLite store: TTL cache + overrides + tested migration | NEXT | :app:testDebugUnitTest | store implementation + tests | no | none |
| M1-5 | debug APK published on rolling `latest-apk` release | NEXT | release API artefact size+date read back | debug-apk + release workflows | no | none |
| M2-0 | live pair search (S1 primary, S2 fallback) | BLOCKED:no captured payload for either source | CI probe verdicts (unmeasured) + docs/spike-verdicts.md | wait for CI probe run OR one capture from phone (§15.5) | no | name the 2nd station code after SMET |

## Metrics (§13.6)
| metric | value |
|---|---|
| defects found before owner sees them | 3 (probe verdict bug caught in-lab; probe S3 vintage test; throttle fixture) |
| defects reaching a phone | 0 |
| UNPROVEN traceability rows | 0 shipped / 0 applicable (nothing shipped yet) |

## Review clock (§13.7)
sources: 2026-10-11 done, next 2026-11-11 (or on gate FAIL) · competitors: due 2027-01-11 ·
open-data S4: monthly until admitted or abandoned twice (next 2026-11-11) · RISK.md: 2026-11-11 ·
RUNBOOK.md: on install/rollback change.

## HANDOFF 2026-10-11 (session 1)
- Milestone state: M0 EXITED (verdicts in docs/spike-verdicts.md). M1 NEXT.
- Artefact ids: probe run 2026-10-11T21:02:29Z; fixtures poc/fixtures/s1_ntes,
  poc/fixtures/s2_erail (2); repo head = first commit on branch kilo/patient-lantern-jgg.
- Resume line: STATUS.md first NEXT row = S1-* (story files), then M1-1.
- Blockers: M2-0 blocked on a captured payload (no parser without a capture, §7.4). Unblock
  paths: (a) read the CI probe verdict once the `checks` job runs on this commit — if S1 or S2
  answers from runners, capture there; (b) needs-device capture from motorola edge 50 neo.
- Rules that must NOT be re-litigated: no key/backend/fabricated values (§3); four provenance
  labels from one label() function (§3.2); EMPTY ≠ FAILURE (§3.3); no parser without a capture
  (§7.4); commit date ≠ data vintage (§10.8); one push per round, read the run before the next
  push (§9.7); humans never run builds (§14.1).
