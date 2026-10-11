# ADR 0003 — S4 open-data portals: UNADMITTED

Date: 2026-10-11 · Status: accepted · Approver: owner (brief §4/S4)

Context: data.gov.in reachable (200), but publication cadence, data vintage and key policy are
unmeasured. A build-time key held as a CI secret does not violate §3.1 but requires ADR + owner
consent.

Decision: S4 stays UNADMITTED. Monthly measurement via the weekly probe until admitted or
abandoned twice. Admission test: cadence + vintage measured, then known-truths gate (10.7) run —
would it have caught S3's three errors?

Consequences: cold-start asset remains empty/labelled until a source is admitted; the app says
"unverified timetable data" rather than pretending completeness. Reversibility: ADR to admit.
Revisit trigger: an official, dated, keyless (or CI-key-held) extract newer than 2023 appears.
