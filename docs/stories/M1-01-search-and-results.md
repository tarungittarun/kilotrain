# STORY M1-01 — search + results screen with honest states

TITLE: station-pair search renders only labelled, sourced rows; every failure has its own words.

OWNER-VISIBLE OUTCOME: a passenger types two station codes, sees trains that exist today with the
railway's current number and name, each line carrying its origin label; a failure says which
source could not be reached, in plain words; an empty answer says "no trains found" and never a
failure.

ENTRY: `poc/probe_sources.py` verdicts known (docs/spike-verdicts.md); contract types exist.

EXIT (executable): `./gradlew :app:testDebugUnitTest` green in a quoted CI run AND a debug APK
published on the `latest-apk` rolling release (size+date read back from the release API).

FAILURE MODES + EXACT COPY (state → copy):
- LIVE: row shows "live data <age> old" + "· via <source>".
- LIVE_STALE: "live data <age> old (may be behind)" — stale per TTL, never silently fresh.
- CACHED: "cached <age>" + last source named.
- OFFLINE_UNVERIFIED: "offline — showing unverified timetable data".
- ANSWERED_EMPTY: "no trains found for <A>–<B> today".
- COULD_NOT_LOOK: "could not reach <source>. showing <what is on screen>".
- PARTIAL: "showing what <source> answered; <what> could not be looked up".
- PROTOCOL_CHANGED: "<source>'s feed may have changed. Nothing to show until it is re-checked."
Platform absent from every source renders as an em dash, never a default.

SIZE BUDGET: offline asset ≤ 6 MB gzipped (KICKOFF 6.7); APK delta ≤ 1.5 MB/release unless ADR'd.

ROLLBACK: republish the previous milestone tag's APK; data rollback = regenerate from the pinned
generator + inputs.

NEEDS-DEVICE? no (until L7). OWNER-ACTION? no.
