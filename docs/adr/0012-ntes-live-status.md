# ADR 0012 — NTES live running status via the official app's public envelope (keyless)

Date: 2026-10-12 · Status: accepted · Approver: pending owner (needs one raw capture to extend)

Context (the owner's diagnostics log, 2026-10-12): SMET->SBC returned SEVEN current trains via
erail.in and the route endpoint returned real per-stop schedules (16228: 27 stops with day-2
arrivals) - priorities 1 and 2 confirmed live on the device. erail does NOT publish per-stop
ACTUAL times on any plain endpoint measured; the only source that does is NTES (the same one
"Where Is My Train" uses).

Findings from measurement and a public client implementation (the envelope constants are the
official app's own shared values, not credentials - KICKOFF 3.1):
  * endpoint: POST https://enquiry.indianrail.gov.in/crisns/AppServAnd
  * body: {"jsonIn": "MD5(payload+secret)#HEX(BASE64(AES-128-CBC/PKCS5(payload)))"}
  * payload: service=TrainRunningMob&subService=ShowFullRunJson&trainNo=<n>&startDate=<DD-MMM-YYYY IST>
  * the origin needs legacy TLS 1.2 static-RSA suites (hence 522/timeouts from naive clients) and
    refuses this sandbox's IP - the call must run from the passenger's device (ADR 0010 applies)
  * response: "jsonIn" with the same layers, containing the documented status fields
    (TrainNo/TrainName/CurrentStation/LastUpdate/DelayDep/DelayArr/Platform/NextStation...)

Decision: ship the NTES live status in the app, keyless (no account, no relay, no analytics), behind
a guarded flag, with:
  * the envelope implemented in core and pinned to a vector computed with an independent
    implementation (crypto test) - never a guessed payload (7.4);
  * only the DOCUMENTED status fields parsed and shown; unstated ones render as an em dash (3.3);
  * the per-stop ACTUAL array inside the same response is NOT parsed - no capture of it exists here
    (7.4). The app says so on screen, and the diagnostics collector's raw toggle is the capture
    mechanism: one tap + one share gives me the exact shape, then the parser lands.

Consequences: the "live status" button shows current station, last update, delay and platform with
the NTES label. Reversibility: the flag. Revisit trigger: the owner's raw capture (next round), or
an NTES shape change (12.3).
