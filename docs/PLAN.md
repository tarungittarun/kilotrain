# PLAN.md — stages, exit criteria, "what changes on the phone"

Stage order is fixed by KICKOFF §7/§8. No stage starts before the previous one exits.

| Stage | Scope | Exit (executable) | Phone-visible change |
|---|---|---|---|
| S0 RESEARCH / M0 | Reachability + shape verdicts for S1/S2; S4 open-data measurement | `python3 poc/probe_sources.py` prints a verdict per source; `docs/spike-verdicts.md` exists; fixtures committed | NONE — research (SAY SO) |
| S1 SPEC | Story files per screen/state with exact failure copy, budgets, rollback, needs-device | Owner read `docs/stories/M1-*.md` and the ONE open question answered | NONE |
| S2 CONTRACT / M1a | Contract types + invariants, pure JVM, tests in CI | `:core:test` green in a real CI run | NONE |
| S3 COMPONENT POC / M1b | Component PoCs registered in COMPONENTS.md, PASS/BLOCKED | Register complete; parsers only against captures | NONE |
| S4 IMPLEMENT / M1c | Vertical slices, flags per source, labelled states on screen | Every state copy asserted by a test; graded APK published | Results screen shows labelled rows/states |
| S5 QA / M1 exit | QA matrix rows green or waived with reason | QA_MATRIX has no silently blank rows | Owner sees the right states on demand |
| S6 RELEASE | Signed release, rolling `latest-apk`, rollback recorded | Artefact size+date READ from release API | Owner installs from one permanent link |

Next stages (M2–M6) open only after M1 exits. M2 acceptance pair: `SMET <-> <owner to name>`.

## Current stage
S0 exit recorded 2026-10-11: `docs/spike-verdicts.md`. M1 in progress.
