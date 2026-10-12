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
            NtesStopStatus(
                stationCode = s.optString("SC").trim().takeIf { it.isNotBlank() },
                stationName = s.optString("SN").trim().takeIf { it.isNotBlank() },
                scheduledArrival = time(s.optString("STA")),
                scheduledDeparture = time(s.optString("STD")),
                actualArrival = time(s.optString("ETA")),
                actualDeparture = time(s.optString("ETD")),
                arrivalDelayMinutes = s.optString("DARR").trim().toIntOrNull(),
                departureDelayMinutes = s.optString("DDEP").trim().toIntOrNull(),
                platform = s.optString("PF").trim().takeIf { it.isNotBlank() },
                isCurrentStation = s.optString("CURR").trim() == "Y",
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

    private fun time(raw: String): String? = raw.trim().takeIf { it.isNotBlank() && it != "null" && it != "0" }
}
