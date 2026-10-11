# CHANGELOG.md — user-facing, per published artefact (KICKOFF §11.6). Artefact size+date READ
from the release API (never assumed).

## Unreleased (repo head)
- **On screen:** nothing yet — no APK published. M0 was research; the first phone-visible change
  lands with M1-3/M1-5.
- **What the tooling proved:** source probe measured S1/S2/S3/S4 from this sandbox
  (docs/spike-verdicts.md). S1 answered 3/3 POSTs (intermittently) but has no envelope capture;
  S2 answers 200 with throttle messages only; S3 falls back to permanently-labelled data;
  S4 unadmitted.
- **Still broken / open:** M2 blocked on a captured payload; no APK yet; CI never ran.
