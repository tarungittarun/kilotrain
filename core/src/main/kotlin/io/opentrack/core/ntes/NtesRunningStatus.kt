package io.opentrack.core.ntes


/**
 * Per-stop live status: scheduled vs ACTUAL times, exactly the documented shape of NTES's answer
 * to the mobile gateway's queryType "6" (the reference the owner supplied). One entry per stop of
 * the active journey.
 *
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
    val isCurrentStation: Boolean,
)

data class NtesRunningStatus(
    val trainNo: String?,
    val currentStatus: String?,
    val stops: List<NtesStopStatus>,
    val fetchedAtMillis: Long,
    val viaCache: Boolean,
)

object NtesRunningStatusParser {

    /**
     * null = the body is not the documented shape (the caller says "protocol may have changed",
     * 12.3). `trainFullStatus` with at least one stop that carries a station code is required;
     * anything else is not inventable.
     */
    fun parse(body: String, nowMillis: Long, viaCache: Boolean): NtesRunningStatus? {
        val json = when (val root = JsonReader.parse(body.trim().removePrefix("\uFEFF"))) {
            is JsonValue.JsonObject -> root
            else -> return null
        }
        val stopsJson = json.getArray("trainFullStatus") ?: return null
        if (stopsJson.size == 0) return null
        val stops = stopsJson.items.mapNotNull { raw ->
            val s = raw as? JsonValue.JsonObject ?: return@mapNotNull null
            NtesStopStatus(
                stationCode = s.optString("stnCode").trim().takeIf { it.isNotBlank() },
                stationName = s.optString("stnName").trim().takeIf { it.isNotBlank() },
                scheduledArrival = time(s, "schArrTime"),
                scheduledDeparture = time(s, "schDepTime"),
                actualArrival = time(s, "actArrTime"),
                actualDeparture = time(s, "actDepTime"),
                arrivalDelayMinutes = s.optString("arrDelay").trim().toIntOrNull(),
                departureDelayMinutes = s.optString("depDelay").trim().toIntOrNull(),
                isCurrentStation = s.optBoolean("isCurrentStn"),
            )
        }
        if (stops.none { it.stationCode != null }) return null
        return NtesRunningStatus(
            trainNo = json.optString("trainNo").trim().takeIf { it.isNotBlank() },
            currentStatus = json.optString("currentStatusStr").trim().takeIf { it.isNotBlank() },
            stops = stops,
            fetchedAtMillis = nowMillis,
            viaCache = viaCache,
        )
    }

    private fun time(station: JsonValue.JsonObject, key: String): String? =
        station.optString(key).trim().takeIf { it.isNotBlank() && it != "null" }
}
