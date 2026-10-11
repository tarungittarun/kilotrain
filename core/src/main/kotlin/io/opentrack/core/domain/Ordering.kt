package io.opentrack.core.domain

import io.opentrack.core.contract.TrainIdentity
import kotlin.math.abs

/**
 * Display order and run-choice are TWO decisions served by TWO comparators (6.5). This class
 * exists because ONE comparator serving both reached a real passenger (10.8).
 */

/** A run of a train: which departure of that number is near the reference time. */
data class TrainRun(
    val identity: TrainIdentity,
    val departsAtEpochDay: Long,
    val departsMinutesOfDay: Int,
)

/**
 * DECISION SERVED: which run of a train number to keep when deduping - the one closest to the
 * reference time. Never used for ordering rows.
 */
object ProximityRunChoice {
    fun choose(runs: List<TrainRun>, referenceEpochDay: Long, referenceMinutes: Int): TrainRun? {
        if (runs.isEmpty()) return null
        val reference = referenceEpochDay * 1440L + referenceMinutes
        return runs.minByOrNull { abs(it.departsAtEpochDay * 1440L + it.departsMinutesOfDay - reference) }
    }
}

/** DECISION SERVED: display order only - still-catchable first, nearest departure on top,
 * already-gone below most-recent-first. */
object RowDisplayOrder {
    /**
     * Written as ONE explicit comparator, not a chain of three: chaining an ascending
     * nearest-first comparator with a descending most-recent-first one lets the first rule win
     * inside the gone group too, which put the oldest departure on top. The decisions are
     * grouped (catchable vs gone) BEFORE any ordering applies (6.5, 10.6).
     */
    val comparator: Comparator<DepartureRow> = Comparator { a, b ->
        when {
            a.stillCatchable != b.stillCatchable -> if (a.stillCatchable) -1 else 1
            a.stillCatchable -> a.minutesUntilDeparture.compareTo(b.minutesUntilDeparture)
            else -> b.minutesUntilDeparture.compareTo(a.minutesUntilDeparture)
        }
    }
}

/** A row ready for display; minutesUntilDeparture is signed (negative = already gone). */
data class DepartureRow(
    val identity: TrainIdentity,
    val minutesUntilDeparture: Int,
) {
    val stillCatchable: Boolean get() = minutesUntilDeparture >= 0
}
