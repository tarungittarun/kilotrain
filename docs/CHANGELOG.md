# CHANGELOG.md — user-facing, per published artefact (KICKOFF §11.6). Artefact size+date READ
from the release API (never assumed).

## Unreleased (M1 in CI)
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
