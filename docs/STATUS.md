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
| M2-0 | live pair search + per-stop ACTUALS (S1; the S2 pair endpoint is SignalR-gated and throttled) | BLOCKED:no captured payload from a source that publishes either | docs/spike-verdicts.md + ADR 0002/0008/0009 | needs-device capture: ONE paste from the NTES app (either running-status for 56269, or between-stations for TLGP->MYS) unlocks both rows | yes | one paste from the NTES app |
| M2-3 | pair search MUST NOT answer from years-old data (owner 2026-10-12) | DONE:2026-10-11 | aPairQueryIsNeverAnsweredFromThe2016Fallback + aPairAbsentFromTheFallbackIsAlsoCouldNotLookNotEmptyAnswer | none | no | none |
| M2-2 | LIVE train-number search via S2 (ADR 0008) | DONE:2026-10-11 | unit-screens 38184388238 PASS (46 tests) + LIVE re-verification 2026-10-11T23:32:27Z: TrainNo=56269 -> 56269 SMET MYS FAST PASS, SMET->MYS, 10.20/16.30, runDays 1111111, stamp 2026-10-06 16:57 IST | none | no | none |

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

## HANDOFF 2026-10-12 (session 4, real emulator in CI)
- The owner asked twice whether workflows can create a real device environment: YES, and it now
  does - reactivecircus/android-emulator-runner@v2 boots a real API-34 AVD and runs L3 green
  (run 38207293417, 15m17s; test anOpenedTrainCorrectsTheColdStartCopy on emulator-5554, 0 failures).
  Same round: checks 38207293395, unit-screens 38207293385, debug-apk 38207293401 all PASS.
  All four CI jobs of every round now pass (instrumented included).
- Standing limit recorded (QA row 12 + LEARNINGS 11): a CI device proves the app, not the data -
  runners are datacenter IPs where NTES times out and erail's pair endpoint throttles. Live pair
  search + per-stop actuals still need ONE capture from the owner's network (STATUS row M2-0).
- Options offered for that capture: (a) one paste from the NTES app / erail.in on the phone;
  (b) a self-hosted runner on the owner's PC (Android Studio emulator on their home network would
  answer NTES directly and automate future captures).

## HANDOFF 2026-10-12 (session 3, owner's stale-data correction)
- Owner tested TLGP->MYS, saw 2016-vintage rows as the answer, and flagged it. Fixed per ADR 0009:
  pair search answers ONLY from a current source; otherwise "could not look this up. no old data
  is shown in its place." Numbers still answer live via S2 (tested live 2026-10-11T23:32:27Z).
- Milestone state: M1 shipped; M2 = live NUMBER search DONE + pair search honestly BLOCKED.
- Artefact ids: runs read this session: 38204200987/988/990/993, 38204465084/088/103/106,
  38204677455/461/464/466, 38204935966/997/008/993. Rolling release: app-debug.apk 9,677,241
  bytes 2026-10-12T04:45:10Z (READ from the release API).
- Resume line: the ONE open owner action (STATUS row M2-0): paste one NTES export; that unblocks
  both live pair search and per-stop actuals.
- Rules not to re-litigate: (previous handoffs) + ADR 0009 - labelled-stale is still stale; a pair
  answer must come from a source measured today, or the screen says it could not look.

## HANDOFF 2026-10-11 (session 2, after M2 live)
- Milestone state: M1 EXITED and shipped; M2 PARTIAL: live train-number search DONE (tested and
  re-verified live by the engineer at 23:32 UTC), pair search on the labelled fallback.
- Artefact ids: runs read this session: 38182204401, 38183246830?, 38183747303/308/314/293,
  38183903524/552/582, 38184051284/287/290/292, 38184215382/445/500/658, 38184388195/221/238/241.
  Rolling release: app-debug.apk 9,677,233 bytes 2026-10-11T23:30:52Z (READ from the release API).
- Resume line: STATUS.md first DOING row (none open for M2-2; next is M3-0's capture), else the
  owner's assurance on M2-2, then M3 when its capture lands.
- Blockers: M3-0 blocked on ONE NTES capture (the only source that publishes per-stop actuals).
  Unblock: NTES app -> 56269 -> running status -> paste the text/screenshot (14.3).
- Rules that must NOT be re-litigated: (as session 1) + the pair endpoint stays flag-OFF until a
  capture exists (7.4); the feed stamp is IST wall time and UTC = stamp - 5:30 (test-enforced).

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
