package io.opentrack.app.source

/**
 * Feature flags per source (12.6). Defaults are the HONEST behaviour, flipped on only by a
 * measured capture (7.4):
 *  * erail train-number: ON - capture taken 2026-10-11 (poc/fixtures/s2_erail/trainno_param.txt).
 *  * erail station-pair: ON, guarded (ADR 0010) - the sandbox and runners are throttled, but the
 *    query runs on the PASSENGER's device, whose network is the one that answers; the parser is
 *    the captured family (7.2/7.4) and any other shape is a stated failure, never a guess.
 *  * NTES (S1): ON, guarded (ADR 0012) - the envelope is the official app's public constants and
 *    the live-status field set is documented; the per-stop ACTUAL array is not captured yet, so the
 *    app shows the documented fields and says what it could not show.
 */
object FeatureFlags {
    const val ERAIL_TRAIN_NUMBER = true
    const val ERAIL_PAIR = true
    const val NTES = true
}
