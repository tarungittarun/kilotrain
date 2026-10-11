# QA_MATRIX.md — the matrix is the deliverable (KICKOFF §10).

Layers: L1 JVM unit/contract · L2 Robolectric screens · L3 emulator flows · L4 emulator + local
mock serving fixtures · L5 monkey + coverage · L6 known-truths gate on shipped asset · L7 owner
phone (reserved for what only a phone can witness).

| # | check | layer | status | evidence |
|---|---|---|---|---|
| 1 | contract types render no time without a provenance label | L1 | pending (M1-1) | test name to be quoted |
| 2 | midnight wrap: due 23:58 seen 00:02 = 4 min late | L1 | pending | named test |
| 3 | 4-letter station codes never truncated (SMET ≠ MET) | L1 | pending | named test |
| 4 | dedupe (proximity) and display order are two comparators | L1 | pending | named tests |
| 5 | aTrainLeavingInNineMinutesOutranksOneThatLeftFortyOneMinutesAgo | L1 | pending | named test |
| 6 | IST fixed regardless of device locale | L1 | pending | named test |
| 7 | device clock wrong → freshness judged by feed stamp | L1 | pending | named test |
| 8 | exact copy asserted for all 8 states | L2 | pending | screen tests |
| 9 | fetch→parse→store→UI with local mock, no railway egress | L4 | pending | instrumented tests (in-test mock server; see ADR 0006) |
| 10 | migration failure clears cache and says so | L1/L2 | pending | store test |
| 11 | known-truths gate verdict recorded in artefact; build stays green on data contradiction | L6 | pending | gate output |
| 12 | /dev/kvm missing → instrumented job reports "untested, with a reason" | L3 | pending | CI log |
| 13 | owner phone: real TLS/egress, Doze alarm, look and feel | L7 | deferred to M4/M6 | needs-device row when opened |

Waived rows (with reason) will be listed here; none waived yet.
