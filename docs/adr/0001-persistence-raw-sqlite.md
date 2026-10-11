# ADR 0001 — persistence: raw SQLite (no Room)

Date: 2026-10-11 · Status: accepted · Approver: owner (via brief §9.1 "pick in an ADR")

Context: store needs cached listings with TTL, per-train live overrides, schema version +
tested migrations (6.4). Room adds KSP + codegen, more plugin/toolchain surface on a machine
with no local Android SDK (compile is CI-only, §9.6).

Decision: raw SQLite via SQLiteOpenHelper with an explicit schema version and tested migrations.

Alternatives rejected: Room (codegen/KSP surface, larger build graph, no benefit at our
row counts); files-as-DB (no transactional override merge).

Consequences: migration tests must exercise real upgrade paths; a migration failure clears the
cache and says so (never shows stale as fresh). Reversibility: high — swap the DAO layer.
Revisit trigger: >3 schema migrations, or async/coroutine-flow ergonomics become the bottleneck.
