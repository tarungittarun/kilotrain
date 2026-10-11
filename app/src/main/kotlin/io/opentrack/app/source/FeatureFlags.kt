package io.opentrack.app.source

/**
 * Feature flags per source (12.6). Defaults are the HONEST behaviour, flipped on only by a
 * measured capture (7.4):
 *  * erail train-number: ON - capture taken 2026-10-11 (poc/fixtures/s2_erail/trainno_param.txt).
 *  * erail station-pair: OFF - every probe returns throttle control messages (measured from
 *    sandbox AND CI runners); no capture, no parser (7.4).
 *  * NTES (S1): OFF - host answers but the envelope has no capture (ADR 0002).
 */
object FeatureFlags {
    const val ERAIL_TRAIN_NUMBER = true
    const val ERAIL_PAIR = false
    const val NTES = false
}
