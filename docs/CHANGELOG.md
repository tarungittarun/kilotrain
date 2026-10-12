# CHANGELOG.md — user-facing, per published artefact (KICKOFF §11.6). Artefact size+date READ
from the release API (never assumed).

## 2.12.0 (rolling `latest-apk`) — NO ECHOED ACTUALS, NO PHONEY DATES
- **On screen:** the NTES rung now shows ACTUAL times only where NTES actually reported them.
  Stops whose ETA/ETD merely repeat the schedule render as an em dash (never the schedule dressed
  as an actual), the "Source"/"Destination" markers are gone, and the query date that made every
  stop read 13-Oct is stripped so times read as plain HH:mm.
- **Why:** NTES echoes the schedule into ETA/ETD when no actual is reported and stamps the origin
  "Source" with the query date - both were shown as if they were fact.

## 2.11.0 (rolling `latest-apk`) — PER-STOP ACTUALS FROM NTES (documented mobile contract)
- **On screen:** when NTES answers, the live-status block now shows per-stop rows in the form
  "SMET Shivamogga Town · sched 22:55/23:00 · actual 23:05/23:10 · late 10 min" - ACTUAL times
  and delay minutes beside the schedule, plus platform, current station and next station from the
  same response. Unstated actuals stay em dashes.
- **The contract, investigated from public clients of the official app's mobile API:** the session
  is WARMED (GET /mntes/, cookies carried into the POST with the Referer) - NTES answers an empty
  body without it, which is the masked-empty you saw; the request is the encrypted ShowFullRunJson
  envelope; the response's per-stop array is STNS with SC/SN/STA/STD/ETA/ETD/PF/DARR/DDEP. All
  three were wrong or missing before; all three are now implemented to that documented shape.

## 2.10.0 (rolling `latest-apk`) — FIVE SOURCES, FIRST ANSWER WINS
- **On screen:** "live status" now races FIVE keyless sources in parallel and displays the FIRST
  answer the phone receives, naming the winner: 1) NTES mobile JSON (queryType 6 - per-stop
  actuals), 2) NTES encrypted envelope (per-stop actuals), 3) erail.in TRAINROUTE (per-stop
  schedule), 4) erail.in train-number payload (identity + endpoint times), 5) the labelled offline
  asset. Slower rungs are cancelled the moment a faster one lands, and the screen lists what the
  others said. If nothing answers it names every source tried.
- **Fixed from your last log's evidence:** the erail schedule rung was never wired into the engine
  ("not installed"), and NTES's masked 2xx-empty answer now falls through to the encrypted envelope
  before reporting anything.

## 2.9.0 (rolling `latest-apk`) — ATTACHABLE DIAGNOSTICS + LIVE-STATUS LOGGING
- **On screen:** the diagnostics panel's file button is now **"save + share file"**: it copies the
  log into the public **Downloads** folder as `opentrack-diagnostics-<time>.jsonl` and shares that
  real file, so it can be attached in a chat or opened from a file manager (the previous share sent
  a private content URI, which many apps refuse).
- **The live-status path now records itself:** every NTES request logs its HTTP status, a structural
  signature of the response, or the typed failure - the gap in the owner's log is closed.

## 2.8.0 (rolling `latest-apk`) — PER-STOP ACTUAL vs SCHEDULED (NTES, reference payload)
- **On screen:** each train row's "live status" now shows the NTES summary PLUS per-stop rows:
  station · scheduled arr/dep · ACTUAL arr/dep · delay in minutes · "here now" marker. Unstated
  actuals are em dashes (never zeros). The block is labelled from the one vocabulary: "live data
  <age> old" fresh, "cached <age> old" for the protected 90-second cache.
- **The three protections are in:** the date is DD-MMM-YYYY with three-letter months; TLS suites
  are widened while certificate verification stays ON (rotated certs fail loudly, never silently);
  the status block is cached per train for 90 seconds so a refresh cannot hammer the endpoint.

## 2.7.0 (rolling `latest-apk`) — LIVE RUNNING STATUS (NTES)
- **On screen:** each train row has a "live status" button: current station, last update, delay
  (dep/arr), platform and next station, from the official NTES enquiry service - keyless. The line
  states plainly that per-stop actual times are not published by this source yet (no capture
  exists, and we do not invent times).
- **Proven by your log:** SMET->SBC -> 7 current trains; stop times for 20652 (16 stops) and
  16228 (27 stops).

## 2.6.0 (rolling `latest-apk`) — STOP TIMES + CONFIRMED LIVE PATHS
- **On screen:** every search now uses LIVE, current sources: pair search (SMET->MYS returns the
  four current trains), train-number search, and a new "stop times" action per row showing the
  per-stop arrival/departure schedule from the measured route endpoint.
- **Measured live today:** pair capture stamped 2026-10-12 10:33 IST (4 trains); train payload
  stamped 2026-10-12 13:34; route capture with stop-by-stop times for 56269.
- **Honest gap:** per-stop ACTUAL (delay) times are NOT published by any keyless plain endpoint I
  could measure - erail exposes them only through its peer network, and NTES needs the APK's
  envelope constants (mirrors block automated download; this sandbox has no pip/RE tooling). The
  app says so instead of inventing delays.

## 2.5.0 (rolling `latest-apk`) — LIVE PAIR SEARCH WORKS (current trains, measured endpoint)
- **On screen:** SMET -> MYS returns the four current trains (16228 TLGP MYS EXP, 16205 MYSURU EXP,
  16226 SMET-MYS UR EXP, 16221 TLGP-MYS KUVEMPU EXPRESS) with departure times, each labelled
  `via erail.in` and an "as of" stamp from the feed itself. Verified against a live capture
  stamped 2026-10-12 10:33 IST.
- **How:** erail's real pair endpoint (parameters read from their own site code — earlier attempts
  used guessed names and were throttled).

## 2.4.0 (rolling `latest-apk`) — PAIR SEARCH QUERIED FROM YOUR OWN PHONE
- **On screen:** station-pair search now makes a REAL request from your phone (its network reaches
  these sources; my CI runners are throttled, yours is not). Results are labelled `via erail.in`
  with the feed's own stamp. If the source does not answer or its shape changed, the screen says
  exactly that - never old rows.
- **Not yet:** per-stop ACTUAL arrival/departure (needs the NTES running-status source, whose
  envelope still has no capture).

## 2.3.0 (rolling `latest-apk`) — NO YEARS-OLD ANSWERS (owner's correction)
- **On screen:** a station-pair search (e.g. TLGP → MYS) now says "could not look this up. no old
  data is shown in its place." with the reason. The 2016-vintage fallback is no longer served as a
  pair answer at all (ADR 0009) - it was labelled but still old, and old is not an answer.
  Train-NUMBER search still answers live from erail.in (ADR 0008), and when it cannot, it says so
  instead of showing old rows.
- **Why:** measured - the S2 pair endpoint answers throttle messages from every environment
  measured, and erail serves its pair search through a private SignalR hub, so no plain pair source
  exists without a capture. Reference app "Where Is My Train" gets pair data from NTES, which needs
  one paste from the owner's phone.

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
