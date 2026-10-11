# ADR 0008 — admit S2's train-number endpoint as the first LIVE source (keyless, captured)

Date: 2026-10-11 · Status: accepted · Approver: pending owner (the owner asked for tested
confirmation first; this ADR grants the build, the owner grants the claim)

Context: the owner's priorities: search by station or train number; details that are current;
and on selection, ACTUAL arrival/departure next to the schedule. The M2 round proved:
  * S1 (NTES): host answers from this sandbox (3/3 POSTs, intermittent) but there is no envelope
    capture, so no parser (ADR 0002);
  * S2 pair endpoint (`?l=&c=`): returns `~~~~~Please try again after some time.` from the
    sandbox (multiple runs, cookies warmed) and from CI runners - throttle, no capture;
  * S2 train-number endpoint (`/rail/getTrains.aspx?TrainNo=<n>`): **measured LIVE 2026-10-11**
    with the documented tilde payload and feed stamp (`~~~~~~2026-10-6-16-57-24~~~^56269~SMET MYS
    FAST PASS~...~10.20~16.30~06.10~1111111~...`). Capture committed at
    poc/fixtures/s2_erail/trainno_param.txt.

Decision: enable the S2 train-number endpoint as the app's first live source, behind
`FeatureFlags.ERAIL_TRAIN_NUMBER = true`, with:
  * the parser written against the committed capture only (7.2/7.4) and tested by
    ErailTrainParserTest + ErailTrainSourceTest with the injected transport;
  * provenance `via erail.in` with the FEED'S OWN stamp as the age (never the device clock,
    10.3; IST fixed, no java.time per 9.4);
  * keyless: no API key, no account, no relay, no analytics (3.1/3.6); INTERNET permission is
    the only new capability;
  * scraping decency: one request per user action, bounded timeouts, cancellable, and a stated
    "live source unreachable: <failure>" fallback to the labelled asset when it does not answer;
  * the station-PAIR endpoint stays flag-off until a capture exists (7.4).

Consequences: train-number search now answers with current identity+schedule (departure,
arrival, duration, run-days as published) labelled `via erail.in`. Per-stop ACTUAL
arrival/departure (priority 3's second half) still needs S1's running-status capture - the app
says so instead of guessing. Reversibility: one flag + one source class.
Revisit trigger: a captured NTES payload (then per-stop actuals), or an erail pair capture.
