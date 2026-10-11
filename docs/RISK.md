# RISK.md — reviewed monthly (KICKOFF §13.7). 2026-10-11.

| risk | impact | likelihood | mitigation | trigger |
|---|---|---|---|---|
| S1/S2 never answer from CI either | M2 slips to phone captures | medium | probe in CI measures; M2 opens only with a capture (7.4) | first `checks` run verdicts |
| source shape changes silently | wrong data shown as live | medium | shape signatures in weekly report; PROTOCOL_CHANGED state | signature delta in weekly run |
| S3/S4 data admitted with bad vintage | phantom/wrong trains | medium | known-truths gate + vintage test (caught 044078) | gate FAIL |
| no local toolchain | compile defects reach CI only | certain | CI is the verifier (§9.6); never claim local green | every round |
| release secrets missing | owner stays on older APK | medium | pipeline publishes debug + states why (11.4) | release job log |
| owner's phone behind AdGuard private DNS blocks a source | L7 surprises | medium | L7 reserved for real egress; probe from device when a row needs it | needs-device row |
| L3 emulator cannot be created on CI runners | M4 alarm proofs stay UNPROVEN on CI | certain | job reports "untested, with a reason" (never a fake pass); re-try on a self-hosted runner or accept L7-only proof for alarms; alarm tests must then be named and honest | an emulator-able runner OR an owner needs-device row |
| key/backend creep | violates §3.1 | low | ADR gate (7.5); §14.6 deliverable is the honest version | any ADR proposing a key |
