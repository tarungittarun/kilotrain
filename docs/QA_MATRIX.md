# QA_MATRIX.md — the matrix is the deliverable (KICKOFF §10).

Layers: L1 JVM unit/contract · L2 Robolectric screens · L3 emulator flows · L4 emulator + local
mock serving fixtures · L5 monkey + coverage · L6 known-truths gate on shipped asset · L7 owner
phone (reserved for what only a phone can witness).

| # | check | layer | status | evidence |
|---|---|---|---|---|
| 1 | contract types render no time without a provenance label | L1 | GREEN | ProvenanceEnforcementTest (unit-screens 38176954520) |
| 2 | midnight wrap: due 23:58 seen 00:02 = 4 min late | L1 | GREEN | RouteStopDelayTest.due2358Seen0002IsFourMinutesLate |
| 3 | 4-letter station codes never truncated (SMET ≠ MET) | L1 | GREEN | ContractTypesTest.fourLetterStationCodesAreNotTruncated |
| 4 | dedupe (proximity) and display order are two comparators | L1 | GREEN | OrderingTest + ProximityRunChoice tests |
| 5 | aTrainLeavingInNineMinutesOutranksOneThatLeftFortyOneMinutesAgo | L1 | GREEN | OrderingTest.aTrainLeavingInNineMinutesOutranksOneThatLeftFortyOneMinutesAgo |
| 6 | IST fixed regardless of device locale | L1 | pending (started M2) | — |
| 7 | device clock wrong → freshness judged by feed stamp | L1 | pending (started M2) | — |
| 8 | exact copy asserted for all 8 states | L2 | GREEN | StateCopyTest (10 assertions, one per state) |
| 9 | fetch→parse→store→UI with local mock, no railway egress | L4 | BLOCKED-with-reason | no source has a captured payload (ADR 0002); no parser exists to prove; QA row stays open until M2's capture |
| 10 | migration failure clears cache and says so | L1 | GREEN | OpenTrackStoreTest.upgradeClearsCacheRatherThanShowingStaleAsFresh |
| 11 | known-truths gate verdict recorded in artefact; build stays green on data contradiction | L6 | GREEN | gate verdict in CI (checks job) + gate_selftest asserts it never exits non-zero on data |
| 12 | instrumented L3 verdict (KVM present on runners) | L3 | FAILED WITH REASON (run 38178541234): "no adb device appeared within 240 s" - the AVD was never created because I hardcoded cmdline-tools/latest for avdmanager; the runner ships a versioned dir. Fixed by deriving avdmanager from the same sdkmanager dir; next run pending | emulator.log tail quoted in run log |
| 13 | owner phone: real TLS/egress, Doze alarm, look and feel | L7 | deferred to M4/M6 | needs-device row when opened |

Waived rows (with reason) will be listed here; none waived yet.
