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
     * Live status line (priority 3). Every field is what NTES published; an unstated one is an em
     * dash. The per-stop ACTUAL array is NOT shown because it is not captured yet - the line says
     * so instead of inventing times (3.4).
     */
    fun liveStatus(status: io.opentrack.app.search.ResultsEngine.StatusOutcome.Live): String = buildString {
        append("live status · ${status.trainName}\n")
        append(status.lastUpdate)
        append("\ndelay: dep ")
        append(status.delayDeparture ?: "—")
        append(" · arr ")
        append(status.delayArrival ?: "—")
        append("\nplatform: ")
        append(status.platform ?: "—")
        append("\nnext station: ")
        append(status.nextStationName ?: "—")
        append("\nvia NTES · per-stop actual times are not published by this source yet")
    }

    /** Provenance-readable row line (10.5). */
    fun row(number: String, name: String, timeDue: String, provenanceLabel: String): String =
        "$number · $name — $timeDue · $provenanceLabel"
}
