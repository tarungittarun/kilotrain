# STATUS.md — the tracker (KICKOFF §13.3). One machine-readable table.

Resume rule: first DOING row, else first NEXT. DONE requires an artefact NAME. One open
owner-action only. Any behaviour-changing commit must change this table (docs-freshness gate).

| id | task | state | verified-by | next action | needs-device | owner-action |
|---|---|---|---|---|---|---|
| M0-1 | docs skeleton (§13.1) committed | DONE:2026-10-11 | git history, first commit | none | no | none |
| M0-2 | capture tool + source probe (§15.3) | DONE:2026-10-11 | `python3 poc/probe_sources.py` → docs/spike-verdicts.md + poc/fixtures/ | weekly CI probe re-measures | no | none |
| M0-3 | probe wired into CI with capture artefacts | DONE:2026-10-11 | CI run 38175068398 PASS, artefact probe-capture-38175068398 | none | no | none |
| S1-1 | story M1-01 (search + results, exact state copy) | DOING | docs/stories/M1-01-search-and-results.md written | owner reads story + TEST_CARD at the first published APK | no | none |
| M1-1 | contract types + provenance + label() (pure JVM) | DOING | core contract tests exist (ProvenanceEnforcementTest et al.); CI verdict unread | read `unit-screens` run, then flip to DONE:2026-10-11 | no | none |
| M1-2 | known-truths gate run by the data build (§10.7) | DONE:2026-10-11 | scripts/known_truths_gate.py + poc/gate_selftest.py; gate verdict in CI | none | no | none |
| M1-3 | labelled states on the results screen | DOING | StateCopyTest exists asserting exact copy for all 8 states; CI verdict unread | read `unit-screens` run, then flip to DONE | no | none |
| M1-4 | SQLite store: TTL cache + overrides + tested migration | DONE:2026-10-11 | OpenTrackStoreTest (TTL, override correction, upgrade clears) | read runs | no | none |
| M1-5 | debug APK published on rolling `latest-apk` release | DONE:2026-10-11 | debug-apk run 38177316606 PASS; release API: app-debug.apk 9,511,991 bytes 2026-10-11T21:42:19Z | none | no | none |
| M1-6 | M1 exit criterion: no time without a provenance label, enforced by a test | DONE:2026-10-11 | unit-screens 38176954520 PASS: ProvenanceEnforcementTest + StateCopyTest + ResultsEngineTest | none | no | none |
| M2-0 | live pair search (S1 primary, S2 fallback) | BLOCKED:no captured payload for either source (CI: S1 blocked-by-network 0/3, S2 throttle-only; sandbox S2 throttled with cookies warmed) | docs/spike-verdicts.md + ADR 0002 | needs-device capture: paste one SMET→MYS export from the NTES app | yes | none - the 2nd station is now known: MYS (owner confirmed by installing the APK) |
| M2-1 | pair search over the labelled S3 fallback (SMET→MYS) | DONE:2026-10-11 | ResultsEngineTest.theOwnerRouteReturnsItsLabelledRow + aPairMissingFromTheFallbackIsNeverAnEmptyAnswer | none | no | none |
| M2-2 | LIVE train-number search via S2 (ADR 0008) | DOING | ErailTrainParserTest + ErailTrainSourceTest + liveNumberSearchLabelsViaTheSourceWithTheFeedStamp | read unit-screens run | no | none |
| M3-0 | per-stop ACTUAL arrival/departure + schedule (priority 3) | BLOCKED:no NTES (S1) capture - only S1 publishes per-stop actuals | docs/spike-verdicts.md + ADR 0008 | needs-device capture: NTES app -> train 56269 -> running status -> paste text/screenshot (14.3) | yes | none |

## Metrics (§13.6)
| metric | value |
|---|---|
| defects found before owner sees them | 12 (probe verdict; S3 vintage test; throttle fixture; docs-gate table parse; gate claim-kind; setup-android action; gradlew scripts; `in.opentrack` package keyword; abs import; test param/returnType; comparator chain + 2 test constants; CI permissions + unbounded emulator wait) |
| defects reaching a phone | 0 |
| UNPROVEN traceability rows | 0 shipped / 3 open, all pending M2 |
| CI rounds read | r1 checks 38175068398 PASS 2m18s · r2 checks 38175814758 PASS 2m15s (gradle jobs failed, fixed) · r3-r8 fix rounds · r9 unit-screens 38176954520 PASS 2m19s, checks 38177316587 PASS 2m18s, debug-apk 38177316606 PASS 2m10s |

## Review clock (§13.7)
sources: 2026-10-11 done, next 2026-11-11 (or on gate FAIL) · competitors: due 2027-01-11 ·
open-data S4: monthly until admitted or abandoned twice (next 2026-11-11) · RISK.md: 2026-11-11 ·
RUNBOOK.md: on install/rollback change.

## HANDOFF 2026-10-11 (session 1 end)
- Milestone state: **M1 EXITED for its exit criterion** ("no time without a provenance label,
  enforced by a test": unit-screens 38176954520 / 38179889632 / 38178953739 PASS) and **SHIPPED**
  (rolling `latest-apk`: app-debug.apk 9,511,991 bytes 2026-10-11T21:42:19Z, permanent URL in
  RUNBOOK). M2 is the open milestone.
- Artefact ids: CI runs read this session: 38175068398, 38175814758, 38176954520, 38177316587,
  38177316606, 38178541224/33/12/34, 38178953739/684/787/830, 38179889524/533/555/632.
- Resume line: STATUS.md first DOING row = S1-1 (owner reads the M1-01 story + TEST_CARD); the
  only open owner-action is M2-0 (name the second station code after SMET; one capture).
- Blockers: M2 needs one captured payload from S1 or S2 (CI says: S1 blocked-by-network 0/3,
  S2 throttle-only; no parser without a capture, 7.4). Unblock: a pasted export from the phone
  (14.3) = Settings->enable diagnostics->open the NTES app for SMET-><route>->Save->paste text.
- Rules that must NOT be re-litigated: no key/backend/fabricated values (3); four labels from
  one label() (3.2); EMPTY != COULD_NOT_LOOK (3.3); no parser without a capture (7.4); commit
  date != data vintage (10.8); one push per round and read the run (9.7); humans never run
  builds (14.1); never a fake pass (11.1).
- Standing note: L3 is UNTESTED on CI runners with a reason (QA row 12); do not re-spend rounds
  on `avdmanager` until a self-hosted emulator-able runner exists or an owner needs-device row
  is open.
