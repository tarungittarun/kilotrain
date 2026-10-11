# RUNBOOK.md — 10 lines (KICKOFF §11.5).

1. Install: open the permanent link of the `latest-apk` rolling release; download `app-debug.apk`; allow install from that source.
2. Update: same link; versionName `2.M.k` tells you the milestone; versionCode rises monotonically.
3. Roll back: in Releases, republish the previous milestone tag's `app-debug.apk`; data rollback = re-run the pinned asset generator with the pinned inputs.
4. Log export: in-app diagnostics must be opt-in; export only when you tap export; the file lands where Android's share sheet puts it.
5. Weekly report: docs/reports/<year>-week<NN>.md has the per-source score table; a red row names the failing source and the threshold it broke.
6. If an install fails: check the artefact size+date in RELEASES.md against the release page; a mismatch means the pipeline published debug instead of release (secrets missing).
