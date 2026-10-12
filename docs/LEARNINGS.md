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
7. **A package name may not start with a Kotlin hard keyword** (`in`), and no local gate here
   can catch it: this machine has no Kotlin compiler. `:core:compileKotlin` failed on every file
   with "Package name must be a '.'-separated identifier list". Fixed by `in.opentrack` ->
   `io.opentrack`. Enforced by: the compile itself, in CI, on every push.
8. **GITHUB_TOKEN is read-only by default** - the release publish failed with HTTP 403 until the
   jobs declared `permissions: contents: write`. And **an unbounded `adb wait-for-device` hangs
   the job**: the L3 job waited 18+ minutes because the emulator never appeared. Every wait in
   CI is now bounded with a stated reason on miss (10.8: no diagnostic may outrank the user's
   request - a hung job hides evidence instead of producing it).
11. **Use the standard emulator action, not hand-rolled avdmanager.** Four rounds died on
    `avdmanager create avd` exiting 0 while writing nothing; `reactivecircus/android-emulator-runner@v2`
    booted a real API-34 emulator and ran the instrumented tests green in 15m. And a real device in
    CI proves the APP (install, navigation, store flows) - it does NOT change network egress, so
    live railway data still needs a capture from the owner's network. Two different problems.
    Its boot is FLAKY (one green, one 600s timeout on the next push), so the job keeps
    continue-on-error and a stated-reason verdict; the durable proof is the green run's test result.
10. **When a CI tool fails silently, instrument the exit code and the file it should have
    written.** `avdmanager create avd` exiting 0 while writing nothing cost four rounds; the
    moment the step checked for `$HOME/.android/avd/opentrack_test.ini` it failed WITH THE
    REASON in 34 s. And a failing job that repeats a red herring on every push is worse than an
    honest "untested, with a reason" verdict (11.1).
9. **A chained comparator silently lets an earlier rule win** where it shouldn't
   (`catchable.then(nearestAsc).then(recentDesc)` ordered the gone group by oldest-first).
   The display-order rule is now one explicit comparator that groups catchable-vs-gone before
   any ordering (6.5). Caught by its own named test in CI.
