# ADR 0009 — pair search answers ONLY from a current source (no years-old rows as answers)

Date: 2026-10-12 · Status: accepted · Approver: owner (explicit: "your data is incorrect or
outdated by years... correct this first")

Context: the owner tested TLGP→MYS and got `16205 · Talaguppa Mysore Intercity Express — 14:45 ·
timetable` from the S3 fallback (2016 vintage) — labelled, but still presented as the answer to a
current question. That is the defect: labelled-stale is not good enough for a pair answer.
Measurements that constrain the fix:
  * S2 pair endpoint (`getTrains.aspx?l=&c=`, and the TrainFrom/TrainTo variants): throttle
    control message from the sandbox and from CI runners, every attempt, cookies warmed;
  * erail's trains-between-stations page drives the pair query through its SignalR hub
    (`invoke("RequestData", …)`), i.e. a private transport, not a plain endpoint;
  * S2 train-number endpoint works live (ADR 0008);
  * S1 (NTES) is the reference app's pair/actual source and needs a phone capture (ADR 0002).

Decision: a station-pair query is answered ONLY by a current source. When none answers, the app
shows COULD_NOT_LOOK with its own words — it never substitutes the 2016 fallback, never renders an
empty list as "no trains", and never consults the pair cache (which may hold pre-rule rows). The
train-number search keeps the live S2 endpoint (ADR 0008) and, when that fails, says so instead of
showing old data. The S3 asset remains in the repo as the ADR-0007 cold-start substrate — gated,
versioned, reserved — and ships only if a NEWER keyless extract appears (12.4); until then it is
not used for search answers.

Consequences: TLGP→MYS now reads "could not look this up. no old data is shown in its place." with
the reason; train numbers still answer live. The owner sees no years-old rows at all.
Reversibility: one commit restores the fallback path if the owner prefers it — but the owner's
instruction is explicit, so the default is honest absence. Revisit trigger: a captured pair source
(NTES phone capture) or a newer keyless extract.
