package io.opentrack.core.domain

import io.opentrack.core.contract.TrainIdentity

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
    private val catchableFirst = compareByDescending<DepartureRow> { it.stillCatchable }
    private val nearestFirst = compareBy<DepartureRow> { it.minutesUntilDeparture }
    private val goneMostRecentFirst = compareByDescending<DepartureRow> { it.minutesUntilDeparture }

    val comparator: Comparator<DepartureRow> =
        catchableFirst.then(nearestFirst).then(goneMostRecentFirst)
}

/** A row ready for display; minutesUntilDeparture is signed (negative = already gone). */
data class DepartureRow(
    val identity: TrainIdentity,
    val minutesUntilDeparture: Int,
) {
    val stillCatchable: Boolean get() = minutesUntilDeparture >= 0
}
