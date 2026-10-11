# ADR 0004 — UI: Jetpack Compose (not Views)

Date: 2026-10-11 · Status: accepted · Approver: owner (brief §6.6 allows either)

Context: 8 screen states, each with exact copy, provenance readable by TalkBack, dark-theme
contrast, font scale 1.15 at 440 dpi. Compose's state-driven UI makes "state → copy" a single
render path with per-state tests, instead of imperative view mutation.

Decision: Kotlin + Compose (BOM), Material3, minSdk 24, one activity; state copy lives in one
file (StateCopy.kt) so screens cannot compose their own labels (§3.2).

Alternatives rejected: Views (more places to mutate text ad hoc; harder to unit-test state copy).

Consequences: compose compiler plugin pinned in the version catalog; R8/reflection exercised by a
release build in CI (9.4). Reversibility: medium. Revisit trigger: Compose toolchain instability
or minSdk-floor device problems.
