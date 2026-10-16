package io.opentrack.core.ntes


/**
 * Per-stop live status: scheduled vs ACTUAL times, in the shape the NTES mobile gateway documents
 * (public clients of the official app's API: `STNS` rows with SC/SN/STA/STD/ETA/ETD/PF/DARR/DDEP).
 * Every field is what the source published; an unstated one stays null and renders as an em dash
 * (3.3) - never a synthesized time (3.4).
 */
data class NtesStopStatus(
    val stationCode: String?,
    val stationName: String?,
    val scheduledArrival: String?,
    val scheduledDeparture: String?,
    val actualArrival: String?,
    val actualDeparture: String?,
    val arrivalDelayMinutes: Int?,
    val departureDelayMinutes: Int?,
    val platform: String?,
    val isCurrentStation: Boolean,
    val distanceKm: String?,
    val haltMinutes: String?,
)

data class NtesRunningStatus(
    val trainNo: String?,
    val currentStatus: String?,
    val currentStationName: String?,
    val lastUpdate: String?,
    val delayDeparture: String?,
    val delayArrival: String?,
    val platform: String?,
    val nextStationName: String?,
    val stops: List<NtesStopStatus>,
    val fetchedAtMillis: Long,
    val viaCache: Boolean,
)

object NtesRunningStatusParser {

    /**
     * null = the body is not a documented shape (caller: SHAPE_UNKNOWN, "protocol may have
     * changed", 12.3). `STNS` with at least one row carrying a station code is required, or the
     * long-key form; nothing else is inventable.
     */
    fun parse(body: String, nowMillis: Long, viaCache: Boolean): NtesRunningStatus? {
        val json = JsonReader.parse(body.trim().removePrefix("\uFEFF"))
            as? JsonValue.JsonObject ?: return null
        val rows = json.getArray("STNS") ?: return null
        if (rows.size == 0) return null
        val stops = rows.items.mapNotNull { raw ->
            val s = raw as? JsonValue.JsonObject ?: return@mapNotNull null
            val schArr = time(s.optString("STA"))
            val schDep = time(s.optString("STD"))
            // NTES may publish actuals under ETA/ETD (documented) or ACTA/ACTD / RA/RD (other
            // public clients). Try each in order; an unstated actual stays null (3.3).
            var actArr = actualTime(s.optString("ETA"))
                ?: actualTime(s.optString("ACTA"))
                ?: actualTime(s.optString("RA"))
            var actDep = actualTime(s.optString("ETD"))
                ?: actualTime(s.optString("ACTD"))
                ?: actualTime(s.optString("RD"))
            // NTES echoes the schedule into ETA/ETD when no actual has been reported. An echo is
            // not an actual: drop it so it renders as an em dash, never a synthesized time
            // (3.3/3.4; never a zero delay for an unreported stop).
            if (actArr != null && actArr == schArr) actArr = null
            if (actDep != null && actDep == schDep) actDep = null
            NtesStopStatus(
                stationCode = s.optString("SC").trim().takeIf { it.isNotBlank() },
                stationName = s.optString("SN").trim().takeIf { it.isNotBlank() },
                scheduledArrival = schArr,
                scheduledDeparture = schDep,
                actualArrival = actArr,
                actualDeparture = actDep,
                arrivalDelayMinutes = s.optString("DARR").trim().toIntOrNull(),
                departureDelayMinutes = s.optString("DDEP").trim().toIntOrNull(),
                platform = s.optString("PF").trim().takeIf { it.isNotBlank() },
                isCurrentStation = s.optString("CURR").trim() == "Y",
                distanceKm = s.optString("DIST").trim().takeIf { it.isNotBlank() && it.toIntOrNull() != null },
                haltMinutes = s.optString("Halt").trim().takeIf { it.isNotBlank() && it.toIntOrNull() != null },
            )
        }
        if (stops.none { it.stationCode != null }) return null
        return NtesRunningStatus(
            trainNo = json.optString("TrainNo").trim().takeIf { it.isNotBlank() },
            currentStatus = json.optString("LUPDT").trim().takeIf { it.isNotBlank() },
            currentStationName = json.optString("CurrentStationName").trim().takeIf { it.isNotBlank() },
            lastUpdate = json.optString("LUPDT").trim().takeIf { it.isNotBlank() },
            delayDeparture = json.optString("DelayDep").trim().takeIf { it.isNotBlank() },
            delayArrival = json.optString("DelayArr").trim().takeIf { it.isNotBlank() },
            platform = json.optString("Platform").trim().takeIf { it.isNotBlank() },
            nextStationName = json.optString("NextStationName").trim().takeIf { it.isNotBlank() },
            stops = stops,
            fetchedAtMillis = nowMillis,
            viaCache = viaCache,
        )
    }

    /**
     * NTES time values are compound: `Source/20:55 13-Oct`, `Destination/08:20 13-Oct`, or a bare
     * `20:55`. The marker and the echoed QUERY date are not the stop's schedule, so only HH:mm is
     * kept (3.4); the embedded date is the query date, which is why every stop once showed 13-Oct.
     */
    private val COMPOUND = Regex("""^(Source|Destination)?\s*/?\s*(\d{1,2}:\d{2})(?:\s+\d{1,2}-[A-Za-z]{3})?$""")

    /**
     * A SCHEDULED time: NTES may stamp the origin "Source/20:55 12-Oct" or the destination
     * "Destination/08:20 13-Oct" - the marker and the query date are not the stop's schedule, so
     * only HH:mm is kept (3.4).
     */
    private fun time(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank() || value == "null" || value == "0") return null
        val match = COMPOUND.find(value)
        if (match != null) {
            val hhmm = match.groupValues[2]
            return if (hhmm.count { it == ':' } == 1) hhmm else value
        }
        return value
    }

    /**
     * An ACTUAL time: the same compound form, PLUS NTES's "not published" markers - blank, "null",
     * "0", "**UA**" - none of which is an actual (3.4). The caller also drops echoes separately.
     */
    private fun actualTime(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank() || value == "null" || value == "0") return null
        if (value.contains("UA")) return null // the **UA** placeholder
        return time(value)
    }
}
