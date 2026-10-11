# ADR 0005 — toolchain pins; compile verified by CI only

Date: 2026-10-11 · Status: accepted · Approver: owner (brief §9.1/§9.6)

Context: the dev sandbox has NO JDK, Gradle or Android SDK (audit 2026-10-11). Local static gates
still run (py_compile, bash -n, docs freshness) but they prove nothing about compile success.

Decision: JDK 17, AGP/Kotlin pinned in gradle/libs.versions.toml (one place), minSdk 24,
targetSdk = latest stable pinned at release time. Compile/unit/screen/instrumented/release
verification happens in GitHub Actions only; reports quote run ids, never "it works here".

Consequences: every round needs one push + reading that run (9.7). A second push cancels the first
run's evidence — forbidden. Reversibility: high (version catalog). Revisit trigger: yearly
compileSdk/targetSdk bump cadence (12.5).
