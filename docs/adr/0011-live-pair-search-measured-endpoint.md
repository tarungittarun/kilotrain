# ADR 0011 — live station-pair search via erail's real endpoint (measured parameters, captured payload)

Date: 2026-10-12 · Status: accepted · Approver: owner ("do it from your side")

Context: the owner's screen showed "SHAPE_UNKNOWN … signature={line_count=1, tilde_count=5 …}"
on the pair search. That signature is the throttle body — proof that erail's plain pair endpoint
refuses GUESSED parameter names, from every network including phones. Reverse-engineering erail's
own site JS (`js5/cmp/erail_all_35.js`) revealed the real call:
`GET /rail/getTrains.aspx?Station_From=<A>&Station_To=<B>&DataSource=AVLSOURCE_IRCTC_NGET&Language=EN&Cache=false`
— a plain AJAX call, no SignalR, no key. Measured 2026-10-12T07:30Z: HTTP 200, 2,954 bytes,
0.06 s, feed stamp `2026-10-12-10-33-33` (the enquiry day), four current trains for SMET→MYS:
16228 TLGP MYS EXP (23.00→08.20), 16205 MYSURU EXP, 16226 SMET-MYS UR EXP, 16221 TLGP-MYS
KUVEMPU EXPRESS — and NOT one retired 0-prefix number, unlike the 2016 S3 asset.

Decision: the app's pair search calls exactly that endpoint (ADR 0010's device-side transport was
already right; the parameter names were wrong). The parser is the captured family, now with TWO
real captures (single-train 2026-10-11, station-pair 2026-10-12) and tests that assert the four
train numbers, names, times, the run-days mask, the stamp, and the absence of retired prefixes.

Consequences: priority 1 (station-pair → current trains) is now satisfied from the passenger's
device with current data, labelled `via erail.in` and dated by the feed's own stamp. The same
response family carries per-stop data in later fields — the next step for priority 3.
Reversibility: the feature flag. Revisit trigger: a signature change (12.3).
