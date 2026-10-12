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
| 12 | instrumented L3 on a REAL API-34 emulator | L3 | **PROVEN** (run 38207293417, 15m17s: emulator-5554 API 34, `anOpenedTrainCorrectsTheColdStartCopy`, 0 failures) **AND FLAKY** (run 38208479376: 'Timeout waiting for emulator to boot' at the 600s default cap - an infrastructure flake, reported as untested-with-reason, never a pass); boot timeout raised to 900s | TEST-emulator-5554-14-_app-.xml + run logs |
| 13 | owner phone: real TLS/egress, Doze alarm, look and feel | L7 | deferred to M4/M6 | needs-device row when opened |

Waived rows (with reason) will be listed here; none waived yet.

| 15 | SmettoMys pair returns its labelled fallback row | L1/L2 | GREEN | ResultsEngineTest.theOwnerRouteReturnsItsLabelledRow |
| 16 | a pair missing from the fallback is never an empty answer (3.3/3.4) | L1/L2 | GREEN | aPairMissingFromTheFallbackIsNeverAnEmptyAnswer + MainActivityTest.anUnknownPairSaysSoInsteadOfClaimingNoTrains |
| 17 | retired 0-prefix numbers never ship in the asset | build | GREEN | asset_selftest transform tests; 176 dropped from the pinned input |
| 18 | asset <= 6 MB gzipped (6.7) | build | GREEN | checks job budget step: 116844 bytes measured |

| 19 | live train-number parse against the CAPTURED payload (7.2) | L1 | GREEN | ErailTrainParserTest.parsesTheCapturedPayload + ErailTrainSourceTest.capturedPayloadBecomesALiveLookup |
| 20 | live failure names itself and falls back to the labelled asset | L1/L2 | GREEN | liveFailureFallsBackToTheLabelledAsset |
| 21 | freshness comes from the feed stamp, never the device clock (10.3) | L1 | GREEN | feedStampIsParsedAsFixedIST |
| 22 | throttle body is SHAPE_UNKNOWN, never a parse (LEARNINGS 1) | L1 | GREEN | throttleBodyIsShapeUnknownNotATrain |

| 23 | a pair query is NEVER answered from years-old data (ADR 0009) | L1/L2 | GREEN | aPairQueryIsNeverAnsweredFromThe2016Fallback + MainActivityTest pair assertion |
| 24 | the APK installs and runs on a REAL emulator (L3) | L3 | GREEN | run 38207293417, test result on emulator-5554 API 34 |
| 25 | COULD_NOT_LOOK copy matches what is actually shown | L2 | GREEN | StateCopyTest.couldNotLookIsNotAnEmptyAnswer |
