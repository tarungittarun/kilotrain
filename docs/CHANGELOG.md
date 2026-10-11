# CHANGELOG.md — user-facing, per published artefact (KICKOFF §11.6). Artefact size+date READ
from the release API (never assumed).

## 2.2.0 (rolling `latest-apk`) — LIVE TRAIN-NUMBER SEARCH (S2, keyless, captured) — TESTED
- **On screen:** type a train number (e.g. 56269) -> "show train" returns the CURRENT identity,
  departure, arrival, duration and run-days from erail.in, labelled `via erail.in` with the
  feed's own stamp ("as of 2026-10-06 16:57"). When the live source cannot answer, the labelled
  2016 fallback appears and the note names the exact failure.
- **Engineer confirmation (not CI alone):** live request 2026-10-11T23:32:27Z parsed to
  56269 · SMET MYS FAST PASS · SMET->MYS · 10.20/16.30 · runDays 1111111; CI run 38184388238
  passes 46 tests including the parser against the committed capture.
- **On screen:** type a train number (e.g. 56269) -> "show train" returns the CURRENT identity,
  departure, arrival, duration and run-days from erail.in, labelled `via erail.in` with the
  feed's own stamp ("as of 2026-10-06 16:57"). When the live source cannot answer, the labelled
  2016 fallback appears and the note names the exact failure.
- **Not yet:** per-stop ACTUAL arrival/departure (needs the NTES capture; ADR 0008) and live
  station-pair search (pair endpoint throttled).

## 2.1.0 (rolling `latest-apk`) — M2 FALLBACK PAIR SEARCH (functional, labelled)
- **On screen:** SMET→MYS now returns **56269 · Shimoga Mysore Passenger — 10:30 · timetable**,
  plus the note that the data is a 2016-vintage fallback, not the railway's current timetable.
  A pair the fallback does not cover says so instead of claiming "no trains found".
- **Still open:** live sources (S1/S2) are blocked and unparsed (ADR 0002/0007); the vintage
  label stays until a capture lands.

## 2.0.0 (rolling `latest-apk`) — M1 HONEST COLD START
- **On screen:** search two station codes; the screen shows labelled, sourced rows or says
  exactly what it could not look up. Today: "offline - showing unverified timetable data. No
  source answered." — no fabricated trains. Install link (permanent):
  https://github.com/tarungittarun/kilotrain/releases/download/latest-apk/app-debug.apk
- **Artefact:** app-debug.apk, 9,511,991 bytes, 2026-10-11T21:42:19Z, READ from the release API.
- **What CI proved (run ids, quoted):** unit-screens 38176954520 PASS 2m19s (L1 contract incl.
  ProvenanceEnforcementTest; L2 exact copy of all 8 states); checks 38177316587 PASS 2m18s;
  debug-apk 38177316606 PASS 2m10s and published the artefact; instrumented verdict read from
  the current round's run (see QA_MATRIX row 12).
- **Defects caught before you saw anything:** 10 (probe verdict, throttle fixture, docs-gate
  table parse, gate claim-kind, setup-android action, gradlew scripts, `in.opentrack` package
  keyword, abs import, test-param/returnType, comparator chain + 2 test constants, + CI
  permissions + bounded emulator wait). None reached a phone.
- **Still broken / open:** M2 live pair search blocked on a captured payload (ADR 0002);
  release signing pending secrets (11.4).
- **On screen:** the first phone-visible change: a search screen that shows only labelled,
  sourced rows and says plainly when it cannot look anything up. Today's build ships an
  admission-free (honest-empty) offline asset, so a search shows "offline - showing unverified
  timetable data. No source answered." — never an invented list.
- **What CI proved:** run 38175068398 (checks: py_compile, bash -n, docs-freshness, gate
  self-test, asset+known-truths gate, source probe with captured artefacts) — PASS, 2m14s.
  The CI probe measured FROM runners: S1 blocked-by-network (0/3 timeouts), S2 blocked-by-shape
  (throttle payloads). Gradle jobs (unit-screens, debug-apk, instrumented, release) added in
  this commit; their run ids appear in RELEASES.md once read.
- **Still broken / open:** M2 live pair search blocked on a captured payload (ADR 0002);
  instrumented L3 runs only when the runner has /dev/kvm; release stays unsigned until the
  owner hands over signing secrets (11.4).
