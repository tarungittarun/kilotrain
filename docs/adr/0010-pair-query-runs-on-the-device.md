# ADR 0010 — the pair query runs on the passenger's device (their network, not ours)

Date: 2026-10-12 · Status: accepted · Approver: owner ("I will not install any other app, do it
from your side and check")

Context: live pair search is the owner's priority 1. Measured constraints:
  * the erail pair endpoint (`getTrains.aspx?l=&c=`) answers throttle control messages from the
    dev sandbox AND from GitHub runners (every variant, cookies warmed);
  * erail's web pair search runs through a private SignalR hub, not a plain endpoint;
  * NTES routes time out from the sandbox and the envelope has no capture (ADR 0002);
  * the owner will not install the NTES app and will not paste a capture;
  * BUT the app already runs on the owner's phone - on a network that reaches these sources.

Decision: ship the pair endpoint as a RUNTIME source queried by the app itself (ADR 0010), so the
request leaves from the passenger's network, not from a datacenter:
  * the parser is the CAPTURED family from 2026-10-11 (same `getTrains.aspx` backend): the
    train-number capture IS a one-train pair response, and `ErailPairParserTest` proves the layout
    on that real capture (7.2/7.4 - no imagined payloads);
  * any body outside the captured family (throttle message, HTML, changed protocol) is a stated
    failure: SHAPE_UNKNOWN + a structural signature (counts only, no values) - never a guess, and
    never years-old rows (ADR 0009);
  * one request per search (3.5); INTERNET already granted and used keylessly (3.1);
  * the truth of the row layout on a MULTI-train pair payload is verified by the shape guard at
    runtime: if erail's pair response does not follow the family, the screen says "the feed may
    have changed" with its signature, and no data is invented.

Consequences: if the passenger's network answers (likely - the reference app proves these sources
serve phones), SMET→MYS now returns current trains labelled `via erail.in` with the feed's stamp.
If it does not, the screen says so precisely. Reversibility: the feature flag.
Revisit trigger: a multi-train capture (from the app's own signature log or any export) that
refines row positions, or the NTES capture for per-stop actuals.
