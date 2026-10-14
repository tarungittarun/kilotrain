package io.opentrack.app.ui

/**
 * The eight states (6.6/10.2) and their EXACT copy. This is the only place that copy lives:
 * screens never compose their own (3.2/3.3). Accessibility: every banner is a full sentence
 * read by TalkBack; no colour-only meaning (10.5).
 */
enum class ScreenState {
    LIVE, LIVE_STALE, CACHED, OFFLINE_UNVERIFIED, ANSWERED_EMPTY, COULD_NOT_LOOK, PARTIAL, PROTOCOL_CHANGED
}

object StateCopy {

    fun banner(state: ScreenState, source: String?, age: String?): String = when (state) {
        ScreenState.LIVE -> "live data ${age ?: "age unknown"} old"
        ScreenState.LIVE_STALE -> "live data ${age ?: "age unknown"} old (may be behind)"
        ScreenState.CACHED -> "cached ${age ?: "age unknown"}"
        ScreenState.OFFLINE_UNVERIFIED ->
            "offline - showing unverified timetable data."
        ScreenState.ANSWERED_EMPTY -> "no trains found for this route today"
        ScreenState.COULD_NOT_LOOK ->
            "could not look this up. no old data is shown in its place."
        ScreenState.PARTIAL ->
            "showing what ${source ?: "a source"} answered; the rest could not be looked up."
        ScreenState.PROTOCOL_CHANGED ->
            "${source ?: "the source"}'s feed may have changed. Nothing to show until it is re-checked."
    }

    /** Per-stop schedule line: only what the source states; an unstated time renders as an em dash. */
    fun stop(code: String, name: String, arrival: String?, departure: String?): String {
        val arr = arrival ?: "—"
        val dep = departure ?: "—"
        return "$code $name — arr $arr · dep $dep"
    }

    /**
     * Live status block (priority 3): the NTES summary plus per-stop SCHEDULED vs ACTUAL times.
     * Unstated values render as em dashes (3.3); nothing is interpolated (3.4). The provenance line
     * comes from the single label() vocabulary - "live data <age> old" for a fresh answer,
     * "cached <age> old" for the protected 90-second cache (3.2).
     */
    fun liveStatusHeader(
        trainNo: String,
        currentStatus: String,
        provenanceLabel: String,
        sourceLabel: String,
    ): String = "train $trainNo · $sourceLabel\n$currentStatus · $provenanceLabel"

    fun stopStatus(
        code: String,
        name: String,
        scheduledArrival: String?,
        scheduledDeparture: String?,
        actualArrival: String?,
        actualDeparture: String?,
        arrivalDelayMinutes: Int?,
        departureDelayMinutes: Int?,
        isCurrentStation: Boolean,
        isTrainHere: Boolean,
        expectedDeparture: String?,
        distanceKm: String?,
        haltMinutes: String?,
        isDepartedHere: Boolean,
        isNextStation: Boolean,
        kmToNext: String?,
    ): String {
        val reported = actualDeparture ?: actualArrival
        val timing = when {
            reported != null -> "actual ${actualArrival ?: "—"}/${actualDeparture ?: "—"}"
            expectedDeparture != null -> "expected $expectedDeparture (not yet reported)"
            else -> "not reported"
        }
        val delay = arrivalDelayMinutes ?: departureDelayMinutes
        val distance = distanceKm?.let { " · ${it} km" } ?: ""
        val halt = haltMinutes?.let { h -> if (h != "0") " · halt ${h} min" else "" } ?: ""
        // the train is BETWEEN stations: mark where it was last reported and where it is going.
        val marker = when {
            isDepartedHere -> "  ⬅ TRAIN LEFT HERE"
            isNextStation && kmToNext != null -> "  ⬅ NEXT · ~${kmToNext} km away"
            isNextStation -> "  ⬅ NEXT STATION"
            isCurrentStation -> "  ⬅ HERE NOW"
            else -> ""
        }
        return "$code $name · sched ${scheduledArrival ?: "—"}/${scheduledDeparture ?: "—"}" +
            " · $timing" +
            (delay?.let { " · late ${it} min" } ?: "") +
            distance + halt + marker
    }

    /**
     * The detail view's position card: one sentence a passenger can read at a glance - where the
     * train was last reported, where it is going, and by when. Computed only from published facts.
     */
    fun positionSummary(
        departedStation: String?,
        nextStation: String?,
        expectedNext: String?,
        delayMinutes: Int?,
    ): String = when {
        departedStation != null && nextStation != null ->
            "🚂 Current: Left $departedStation\n" +
            "📍 Next: $nextStation" +
            (expectedNext?.let { " · ETA ~ $it" } ?: "") +
            (delayMinutes?.let {
                if (it > 0) " · Running $it min late"
                else if (it < 0) " · Running ${-it} min early"
                else " · On time"
            } ?: "")
        departedStation != null -> "🚂 Left $departedStation (en route to destination)"
        nextStation != null -> "🚂 Approaching $nextStation" +
            (expectedNext?.let { " · ETA ~ $it" } ?: "")
        else -> "Position not reported yet by the live source"
    }

    /** The schedule-only rung's line: what the source states, nothing more. */
    fun routeStop(code: String, name: String, timeDue: String?): String =
        "$code $name · ${timeDue ?: "not stated"}"

    /** The detail header's provenance line (3.2): which source, how old. */
    fun sourceBadge(sourceLabel: String, provenanceLabel: String): String =
        "via $sourceLabel · $provenanceLabel"

    /** Provenance-readable row line (10.5). */
    fun row(number: String, name: String, timeDue: String, provenanceLabel: String): String =
        "$number · $name — $timeDue · $provenanceLabel"
}
