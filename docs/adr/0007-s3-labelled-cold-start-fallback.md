# ADR 0007 — admit S3 (datameet/railways) as the LABELLED cold-start fallback

Date: 2026-10-11 · Status: accepted · Approver: owner (the owner asked for a fully functional
APK; this is the only path that stays inside §3 while doing it)

Context: M2 needs trains-between-stations. S1 is blocked-by-network from CI (0/3 timeouts,
measured 2026-10-11, run artefact probe-capture-*) and no envelope capture exists; S2 answers
200 with throttle control messages from both sandbox and CI. The owner installed the M1 APK and
named the acceptance pair SMET→MYS. Without an admitted data source the app can only say "I do
not know" - honest, but not functional.

Decision: admit S3 `datameet/railways` trains.json, PINNED at commit
e0c538a1e41ae5eace454d2818902e1065608e16, as the **cold-start fallback only, permanently
labelled**, fetched at build time by CI (never from the app - 3.5) and transformed by
`scripts/build_offline_asset.py`:
  * rows whose number uses the retired 0-prefix are DROPPED (06228 was sitting in the SMET→MYS
    pair: shipping it would be a fabricated identity - 3.4); 176 dropped from the pinned input,
    84 more for incomplete identity;
  * geometry and unused fields are stripped (6.7: 116 KB gzipped, budget 6 MB);
  * the artefact carries its measured vintage (2016) on its face; a build stamp is never a
    vintage (10.8);
  * the app labels every row `timetable` with the source "offline fallback" and states the
    vintage in the on-screen note;
  * a pair ABSENT from the fallback is NEVER rendered as "no trains found" - the fallback is
    incomplete, so absence is "could not look", not "answered empty" (3.3/3.4);
  * S1/S2 feature flags stay OFF (12.6) and turn on only with a captured payload (ADR 0002).

Alternatives rejected: waiting for a phone capture with no app in the owner's hands (the owner
asked for a functional APK); third-party keyed APIs (3.1); presenting fallback data without the
vintage label (3.2/10.8).

Consequences: the SMET→MYS search now returns one real train (56269, 10:30→16:55) labelled
`timetable`; the app remains honest about being a 2016 fallback; when a live capture arrives,
M2's live path replaces the fallback per pair without changing the labels. Reversibility: high -
the asset is regenerated from the pin; demoting S3 is a one-commit generator change.
Revisit trigger: a dated, keyless extract newer than 2023 appears (12.4), or a phone capture
lands (15.5).
