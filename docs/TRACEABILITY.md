# TRACEABILITY.md — one row per user-visible promise (KICKOFF §13.4).

promise -> story -> PoC -> test -> CI run id -> release tag -> verdict (PROVEN/UNPROVEN/WAIVED)

| promise | story | PoC | test | run id | release | verdict |
|---|---|---|---|---|---|---|
| "a search never shows a time without its origin label" | M1-3 | probe/contract | (M1-1 tests) | — | — | UNPROVEN (M1 in progress) |
| "empty answer and could-not-look are visibly different" | M1-3 | — | state copy tests | — | — | UNPROVEN |
| "unreachable live data says so in passenger words" | M1-3 | — | state copy tests | — | — | UNPROVEN |

Target: zero UNPROVEN rows for anything shipped. Count appears in every PR body. Currently 0 rows
shipped; 3 rows open pending M1.
