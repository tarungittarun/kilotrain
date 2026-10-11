# LEARNINGS.md — lessons and the artefact that enforces each.

1. **A 200 with the right delimiter is not data.** First probe pass called S2 "usable" because
   the body contained `~`; the body was `~~~~~Please try again after some time.`
   Enforced by: the probe's `real_data` predicate (rows + documented feed stamp required) and the
   committed fixture `poc/fixtures/s2_erail/documented_base_path.txt` + its signature.
2. **A single timeout is not a verdict.** S1's GET timed out once; POST then answered 3/3 (one
   19.7s outlier). Verdicts now carry sample counts.
   Enforced by: probe retry + 3-sample structure.
3. **A repo commit date is not a data vintage.** `datameet/railways` updated 2026-10-11 contains
   retired 0-prefix number `044078` in its first 2 KB. Enforced by: the S3 vintage signal in the
   probe; the known-truths gate must include the retired-prefix pattern when it runs on data.
4. **Intermittent beats "blocked".** The brief records S1 as unreachable from sandboxes (3
   confirmations); today it answered 3/3 POSTs. Re-measure, record, do not re-litigate; verdicts
   say WHERE and WHEN measured.
5. **No local toolchain here.** This machine has no JDK/Gradle/Android SDK (audit 2026-10-11), so
   nothing about compile success is ever claimed locally (§9.6/§11.7): CI decides, and reports
   quote the run.
6. **`android-actions/setup-android@v3` is broken on current runners** (it fails installing the
   retired `tools` SDK package: "Warning: Failed to find package 'tools'"). Caught by CI on the
   M1 round before any claim of green; fixed by installing platform 35 + build-tools directly
   with the runner's own `sdkmanager`. Enforced by: every gradle job uses that step; the failure
   log is quoted in STATUS's metrics (defect caught before the owner saw it).
