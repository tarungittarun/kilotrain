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
    ): String {
        val marker = if (isTrainHere) "  ⬅ TRAIN HERE" else ""
        val reported = actualDeparture ?: actualArrival
        val timing = if (reported != null) {
            "actual ${actualArrival ?: "—"}/${actualDeparture ?: "—"}"
        } else if (expectedDeparture != null) {
            "expected $expectedDeparture (not yet reported)"
        } else {
            "actual — (not reported)"
        }
        val delay = arrivalDelayMinutes ?: departureDelayMinutes
        return "$code $name · sched ${scheduledArrival ?: "—"}/${scheduledDeparture ?: "—"}" +
            " · $timing" +
            (delay?.let { " · late ${it} min" } ?: "") +
            marker
    }

    /** Provenance-readable row line (10.5). */
    fun row(number: String, name: String, timeDue: String, provenanceLabel: String): String =
        "$number · $name — $timeDue · $provenanceLabel"
}
