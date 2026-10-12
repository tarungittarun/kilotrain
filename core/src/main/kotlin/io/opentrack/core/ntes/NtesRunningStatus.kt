package io.opentrack.core.ntes

import org.json.JSONArray
import org.json.JSONObject

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
        val json = try {
            JSONObject(body.trim().removePrefix("﻿"))
        } catch (e: Exception) {
            return null
        }
        val stopsJson: JSONArray = json.optJSONArray("trainFullStatus") ?: return null
        if (stopsJson.length() == 0) return null
        val stops = buildList {
            for (i in 0 until stopsJson.length()) {
                val s = stopsJson.optJSONObject(i) ?: continue
                val code = s.optString("stnCode").trim().takeIf { it.isNotBlank() }
                add(
                    NtesStopStatus(
                        stationCode = code,
                        stationName = s.optString("stnName").trim().takeIf { it.isNotBlank() },
                        scheduledArrival = time(s, "schArrTime"),
                        scheduledDeparture = time(s, "schDepTime"),
                        actualArrival = time(s, "actArrTime"),
                        actualDeparture = time(s, "actDepTime"),
                        arrivalDelayMinutes = s.optString("arrDelay").trim().toIntOrNull(),
                        departureDelayMinutes = s.optString("depDelay").trim().toIntOrNull(),
                        isCurrentStation = s.optString("isCurrentStn").trim() == "true",
                    )
                )
            }
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

    private fun time(station: JSONObject, key: String): String? =
        station.optString(key).trim().takeIf { it.isNotBlank() && it != "null" }
}
