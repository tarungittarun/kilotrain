package io.opentrack.core.domain

/**
 * Search window: -24 h / +24 h around the requested date (6.5). Pure epoch-day arithmetic,
 * no java.time (9.4: java.time needs API 26 or desugaring).
 */
object SearchWindow {
    const val BACK_DAYS = 1
    const val FORWARD_DAYS = 1

    fun range(centreEpochDay: Long): LongRange =
        (centreEpochDay - BACK_DAYS)..(centreEpochDay + FORWARD_DAYS)
}

/**
 * Weekly run-days as a 7-bit mask, bit 0 = Sunday. 1970-01-01 was a Thursday, so
 * dayOfWeek(0) == 4. No java.time anywhere (9.4).
 */
object RunDays {
    const val SUNDAY = 1 shl 0
    const val MONDAY = 1 shl 1
    const val TUESDAY = 1 shl 2
    const val WEDNESDAY = 1 shl 3
    const val THURSDAY = 1 shl 4
    const val FRIDAY = 1 shl 5
    const val SATURDAY = 1 shl 6
    const val ALL_DAYS = 0b1111111

    /** 0 = Sunday .. 6 = Saturday, from an epoch day. */
    fun dayOfWeek(epochDay: Long): Int = (((epochDay % 7) + 4) % 7).toInt()

    fun maskForDayOfWeek(dow: Int): Int = 1 shl dow

    fun runsOn(mask: Int, epochDay: Long): Boolean =
        mask and maskForDayOfWeek(dayOfWeek(epochDay)) != 0
}
