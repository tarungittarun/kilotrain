package io.opentrack.core.parse

/**
 * Structural signature matching (7.4): the capture tool records keys/nesting/lengths, never
 * values; this type is the compile-side half of that contract. It is deliberately a pure data
 * check so a parser can REFUSE a payload before touching it.
 *
 * S2's measured 200 responses are throttle control messages - this matcher is what keeps them
 * out of the parsing path.
 */
data class ShapeSignature(
    val delimiterCounts: Map<Char, Int>,
    val hasFeedStamp: Boolean,
    val lineCount: Int,
) {
    /** A trains-between-stations payload must be multi-row, tilde-separated and stamp-bearing. */
    fun looksLikeTrainsPayload(): Boolean =
        lineCount > 1 &&
            (delimiterCounts['~'] ?: 0) > 0 &&
            hasFeedStamp

    companion object {
        /** The measured throttle body: `~~~~~Please try again after some time.` (2026-10-11). */
        val THROTTLE_SHAPE = ShapeSignature(
            delimiterCounts = mapOf('~' to 5),
            hasFeedStamp = false,
            lineCount = 1,
        )
    }
}
