package in.opentrack.core.contract

private const val MINUTES_PER_DAY = 24 * 60

/**
 * A stop with scheduled vs actual times, a nullable platform (null renders as an em dash -
 * never a default platform, 3.4) and wrap-safe delay maths.
 *
 * [dayOffset] is the day the time falls on RELATIVE to the scheduled day, so 00:02 after a
 * 23:58 due time is dayOffset 1 and 4 minutes late - never 14 h early (10.3).
 */
data class RouteStop(
    val stationCode: String,
    val scheduledMinutesOfDay: Int,
    val actualMinutesOfDay: Int?,
    val platform: String?,
    val dayOffset: Int,
    val provenance: Provenance,
) {
    init {
        require(scheduledMinutesOfDay in 0 until MINUTES_PER_DAY) { "scheduled time out of range" }
        require(actualMinutesOfDay == null || actualMinutesOfDay in 0 until MINUTES_PER_DAY) {
            "actual time out of range"
        }
        require(stationCode.isNotBlank()) { "station code blank" }
    }

    private fun totalMinutes(minutesOfDay: Int, offset: Int) = offset * MINUTES_PER_DAY + minutesOfDay

    /** Minutes late (negative = early). null when no actual was reported - never 0 (3.4). */
    fun delayMinutes(): Int? {
        val actual = actualMinutesOfDay ?: return null
        return totalMinutes(actual, dayOffset) - totalMinutes(scheduledMinutesOfDay, 0)
    }

    /** Platform exactly as stated, or null (the UI draws the em dash). */
    fun platformOrNull(): String? = platform
}
