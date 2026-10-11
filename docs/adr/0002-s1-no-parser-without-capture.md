# ADR 0002 — S1 NTES/CRIS: no parser without a capture; feature flag off

Date: 2026-10-11 · Status: accepted · Approver: owner (brief §7.4)

Context: S1's envelope (AES-CBC + MD5 framed) is public in the official APK but we hold no
captured payload; guessing a shape burns rounds (7.4, 10.8). Today's probe measured the host
reachable from this sandbox 3/3 POSTs (intermittently, one 19.7s outlier) — contradicts the
recorded "sandbox blocked" measurement; recorded, not re-litigated.

Decision: no NTES parser, no NTES client behind a shipping screen until a real capture exists.
S1 exists in code only behind a feature flag, default OFF, with SHAPE_UNKNOWN handling.

Consequences: M2 cannot ship NTES as primary source yet; the UI copy names the missing source
honestly (§3.1/§14.6). Reversibility: drop-in once a capture lands. Revisit trigger: a capture
from CI (probe) or the owner's phone (needs-device row).
