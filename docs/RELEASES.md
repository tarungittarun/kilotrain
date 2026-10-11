# RELEASES.md — release ledger (KICKOFF §11.5/§11.6).

| tag | artefact | size+date (READ from release API) | what changed on screen | rollback |
|---|---|---|---|---|
| latest-apk (rolling) | app-debug.apk | **9,511,991 bytes · 2026-10-11T21:42:19Z** (READ from releases/tags/latest-apk) | first phone-visible change: search screen that states its data-trust honestly; with no source admitted it says "offline - showing unverified timetable data. No source answered." and shows no rows | previous artefact had none (first release) |

Rollback rule: every accepted milestone is tagged; rollback = republish the tagged artefact.
Data rollback = regenerate from the pinned generator + inputs.
