# OpenTrack India v2 — First Slice Plan (S0 Audit → M0 Spike → M1 Honest Cold Start)

## Context

- Repo: `github.com/tarungittarun/kilotrain` (remote configured, `main`), current branch
  `kilo/patient-lantern-jgg`. Tree is empty except `.kilo/` — genuinely "start from scratch".
- Placeholders resolved from the brief: `[REPO]` = above; `[MINSDK]` = 24 (recommended);
  `[BUDGET_MB]` = 6; `[OWNER_DEVICE]` = Motorola edge 50 neo, Android 16, Wi-Fi + 5G behind
  AdGuard private DNS.
- Known facts that this plan does NOT re-litigate: S1 (NTES/CRIS) TLS dies from GitHub runners and
  from sandboxed dev machines (3 independent confirmations); S3 GitHub dumps failed the
  known-truths check (pre-2022-23 renumbering content, reverse-keyed train); S4 open-data is an
  unadmitted candidate to be measured; S5 third-party APIs are forbidden as runtime providers.
- Delivery model works (answers the owner's opening question): code develops here in the sandbox;
  integration, build, QA and release run on GitHub Actions; artifacts publish to a rolling
  `latest-apk` release the owner installs from one permanent link. Where CI is unreachable from
  this session, §11.7 governs (write workflows + scripts, run what's runnable, paste real output,
  never claim an unverified pass).
- FLAG: the remote URL embeds an access token. It lives only in local git config (not committed),
  but the owner should rotate it at the next secret handling step and prefer a credential helper.

## Ordered tasks

### 1. Audit (S0 entry, no code, prints real output)
1. Record toolchain reality: JDK version, Gradle/AGP availability, Android SDK presence,
   `python3 --version`, `bash -n` availability. If no Android SDK locally, state it explicitly and
   let CI decide compile success (§9.6).
2. Read CI state for the current head (`gh run list`) and quote verbatim; if none, say
   "no runs yet — workflows not created".
3. Append findings to `docs/KICKOFF.md` (the pasted brief becomes KICKOFF.md verbatim, with
   placeholders filled).

### 2. Docs skeleton (§13.1) — first commit
Create and commit: `docs/KICKOFF.md`, `docs/PLAN.md`, `docs/STATUS.md` (milestone table, states
per §13.3), `docs/COMPONENTS.md` (PoC register), `docs/DATA_SOURCES.md` (the §4 ledger with
"last measured 2026-10-11" dates), `docs/COMPETITORS.md`, `docs/LEARNINGS.md`, `docs/QA_MATRIX.md`,
`docs/TRACEABILITY.md`, `docs/RISK.md`, `docs/CHANGELOG.md`, `docs/RELEASES.md`, `docs/RUNBOOK.md`
(10 lines: install / rollback / log export / weekly report), `docs/TEST_CARD.md`, `docs/stories/`
and `docs/adr/` directories. Commit explains WHY (evidence trail per §13).

### 3. M0 — capture tool + source probe (§15.3, §7.4)
1. `poc/probe_sources.py` (python3, standalone, JVM-runnable not required — it is the probe):
   for each source S1–S4 it makes a handful of requests per user-action-equivalent, measures
   reachability/latency/shape, and prints one verdict per source:
   `usable | usable-but-labelled | blocked-by-network | blocked-by-shape`.
2. Capture behavior: on any unparsed response, write a redacted fixture plus a structural
   signature (keys, nesting, array lengths — NO values) to `poc/fixtures/` and log the signature.
   No parser is written against an imagined payload.
3. Probe specifics: S1 POST envelope to `enquiry.indianrail.gov.in/crisns/AppServAnd` (expect
   network-blocked from sandbox and CI — record verbatim error); S2 `erail.in/rail/getTrains.aspx`
   flat `~`-text incl. payload feed stamp; S3 GitHub dumps re-measured against the 2022-23
   renumbering test (reject any pre-renumbering content); S4 data.gov.in: measure cadence, vintage,
   whether a free key is needed (ADR + owner consent before any use).
4. Write `docs/spike-verdicts.md`; wire the probe into a CI job that uploads capture artefacts.
   Report which sources answered from the sandbox vs CI — that decides the M2 branch.
   Phone-visible change: NONE — labelled research, SAY SO in the report.

### 4. M1 — honest cold start (§15.4)
Vertical slices, one behaviour per commit, feature flag per source:
1. Gradle/Kotlin project: JDK 17, AGP current-stable, `minSdk 24`, `targetSdk` latest stable,
   Kotlin + coroutines + WorkManager, no analytics SDKs. ADR for Room vs raw SQLite.
2. `contract/` (pure JVM, no android.*/HTTP/DB): `TrainIdentity`, `RouteStop` (nullable platform,
   dayOffset, wrap-safe delay maths), `PairListing` (EMPTY ≠ FAILURE as different types),
   `Provenance(kind, source, fetchedAtMillis)` as a REQUIRED constructor arg with the TTL table
   (LIVE_STOPS 5m, LIVE_PAIR 10m, LIVE_IDENTITY 24h, CACHE 6h, ASSET 90d) and ONE `label()`
   function; `fetchedAtMillis <= 0` is least-trusted, never "now".
3. `source/`: one class per source, transport injected as a function; typed failures
   `OFFLINE|TIMEOUT|HTTP|EMPTY|PARSE|SHAPE_UNKNOWN`.
4. `store/`: SQLite cache with TTL + per-train OVERRIDES (identity + route + fetchedAt + source),
   schema version + tested migrations; migration failure clears cache and says so.
5. `domain/`: search window −24h/+24h, run-days filtering, dedupe by number (proximity) separate
   from display order comparator — ONE comparator never serves both; documented per comparator.
6. `ui/`: Kotlin (Compose or views per ADR) rendering the 8 states
   LIVE / LIVE_STALE / CACHED / OFFLINE_UNVERIFIED / ANSWERED_EMPTY / COULD_NOT_LOOK / PARTIAL /
   PROTOCOL_CHANGED, each with its exact §-approved copy; every value provenance-labelled.
7. Known-truths gate (§10.7): tiny human-verified dated fixture outside the codebase (a handful of
   train identities/routes/pair-presence, NO minutes), run by the data build, verdict recorded in
   the artifact, honoured by the UI ("unverified data" says so); build does NOT go red on a data
   contradiction.
8. Tests: contract tests incl. midnight wrap (23:58→00:02 = 4 min late), 4-letter codes (SMET),
   reverse-keyed train, IST fixed, wrong device clock judged by feed stamp; the enforcement test
   "no time renders without a provenance label"; fallback tests run with the primary dead (§7.3).
   Edge battery names per §10.3; every fixed defect gets a failing-first test named after the
   symptom (§10.6).

### 5. CI + release (§11)
1. Workflows: `checks` (static gates + known-truths gate + docs freshness vs STATUS.md),
   `unit+screens` (L1 JVM + L2 Robolectric), `debug-apk` (build + publish), `instrumented`
   (L3–L4 on emulator with local mock at 10.0.2.2 serving captured fixtures; detects `/dev/kvm`,
   reports "untested, with a reason" when missing — never a fake pass), `release` (tags only,
   signed, R8-shrunk), `weekly-data-watch` (§12.1).
2. Every job prints what it PROVED / SKIPPED and why; failures post error lines as comments, tee
   full log as artefact, keep progress logs (§11.2).
3. Delivery: rolling tag `latest-apk` holding `app-debug.apk` (permanent install link) + signed
   assets on version tags; versionName `2.M.k`, versionCode derived in one place; signing keys in
   CI secrets held by the owner; missing secret ⇒ publish debug and say why.
4. Ship M1: published `latest-apk` artefact + `TEST_CARD.md` (5 lines for the phone).

### 6. Session close
Append the §13.5 HANDOFF block to STATUS.md (milestone state, artefact ids, resume line,
blockers with unblock commands, rules not to re-litigate). One push, then read the run before the
next push (§9.7).

## Rules for the implementer
- One push per round, then read that CI run before the next push.
- Never ask the human to run builds/tests/emulator (§14.1); device asks go through
  `needs-device: yes` rows naming ONE action (§14.2); captured evidence comes as PASTED TEXT
  (§14.3).
- Any new dependency, permission, source promotion, or CI credential touching needs
  `docs/adr/NNN-<slug>.md` (§7.5).
- No parser without a captured payload; no fabricated values; provenance labels via the single
  `label()` function only.

## Decision (owner, 2026-10-11)
M2's acceptance pair is anchored on `SMET`; the owner will name the second station code when M2
opens. Record `SMET <-> <TBD>` as the single open owner-action row in STATUS.md (`needs-device: no`,
`owner-action: name second station code`). It seeds the known-truths fixture. MINDS: M0/M1 (tasks
1–5) are unaffected and proceed now.

## Not in scope for this slice
M2 live pair search, M3 running status, M4 self-healing/alarms, M5 release hardening details,
M6 polish — each opens only after the previous milestone exits (§8).
