# TEST_CARD.md — owner-facing, 5 lines max. What to check on the phone this round.

Round 11 (fix the log share + catch why live status fails): install the new `latest-apk`.
1. Turn ON **diagnostics** (and "include raw responses").
2. Search SMET → SBC, tap any train's **live status**.
3. Tap **"save + share file"** - it saves `opentrack-diagnostics-<time>.jsonl` into your
   **Downloads** folder and hands you a normal file you can attach anywhere. Send it to me.
The new build records the live-status request itself, so the log will say exactly why it failed
(status, response signature or failure kind) - no more silent gap.
