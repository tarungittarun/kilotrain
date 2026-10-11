# TRACEABILITY.md — one row per user-visible promise (KICKOFF §13.4).

promise -> story -> PoC -> test -> CI run id -> release tag -> verdict (PROVEN/UNPROVEN/WAIVED)

| promise | story | PoC | test | run id | release | verdict |
|---|---|---|---|---|---|---|
| "a search never shows a time without its origin label" | M1-01 | contract | ProvenanceEnforcementTest | 38176954520 | latest-apk | PROVEN |
| "empty answer and could-not-look are visibly different" | M1-01 | — | StateCopyTest.answeredEmptyCopy / couldNotLookNamesTheSourceAndIsNotAnEmptyAnswer | 38176954520 | latest-apk | PROVEN |
| "unreachable live data says so in passenger words" | M1-01 | — | StateCopyTest.offlineUnverifiedCopy / protocolChangedCopy | 38176954520 | latest-apk | PROVEN |
| "the app works offline with honest labels" | M1-01 | — | MainActivityTest.firstRenderIsHonestUnverifiedNeverAnEmptyAnswer | 38176954520 | latest-apk | PROVEN |
| "a search matches the live source" | M2-* | probe | — | — | — | UNPROVEN (M2 blocked on a capture; ADR 0002) |

Target: zero UNPROVEN rows for anything shipped. Count appears in every PR body. Currently 0 rows
shipped; 3 rows open pending M1.
