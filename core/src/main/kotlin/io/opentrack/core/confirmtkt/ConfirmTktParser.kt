package io.opentrack.core.confirmtkt

import io.opentrack.core.ntes.JsonReader
import io.opentrack.core.ntes.JsonValue

object ConfirmTktParser {
    fun parse(body: String, nowMillis: Long, viaCache: Boolean = false): ConfirmTktRunningStatus? {
        val json = JsonReader.parse(body.trim().removePrefix("﻿")) as? JsonValue.JsonObject ?: return null
        // Capture-first: if shape doesn't match expected fields, return null (SHAPE_UNKNOWN)
        val stops = mutableListOf<ConfirmTktStop>()
        // Try common shapes; expand when real capture available
        (json.getArray("stops") ?: json.getArray("STNS") ?: json.getArray("route"))?.let { arr ->
            arr.items.forEach { raw ->
                val s = raw as? JsonValue.JsonObject ?: return@forEach
                stops.add(
                    ConfirmTktStop(
                        stationCode = s.optString("code").ifBlank { s.optString("stationCode") }.ifBlank { null },
                        stationName = s.optString("name").ifBlank { s.optString("stationName") }.ifBlank { null },
                        scheduledArrival = s.optString("schArr").ifBlank { s.optString("sta") }.ifBlank { null },
                        scheduledDeparture = s.optString("schDep").ifBlank { s.optString("std") }.ifBlank { null },
                        actualArrival = s.optString("actArr").ifBlank { s.optString("eta") }.ifBlank { null },
                        actualDeparture = s.optString("actDep").ifBlank { s.optString("etd") }.ifBlank { null },
                        arrivalDelayMinutes = s.optString("delayArr").toIntOrNull() ?: s.optString("lateArr").toIntOrNull(),
                        departureDelayMinutes = s.optString("delayDep").toIntOrNull() ?: s.optString("lateDep").toIntOrNull(),
                        platform = s.optString("platform").ifBlank { s.optString("pf") }.ifBlank { null },
                        isCurrentStation = s.optString("current").equals("true", true) || s.optString("isCurrent").equals("true", true),
                        distanceKm = s.optString("distance").ifBlank { s.optString("dist") }.ifBlank { null },
                        haltMinutes = s.optString("halt").ifBlank { s.optString("haltMins") }.ifBlank { null },
                    )
                )
            }
        }
        if (stops.isEmpty()) return null
        return ConfirmTktRunningStatus(
            trainNo = json.optString("trainNo").ifBlank { json.optString("trainNumber") }.ifBlank { null },
            currentStatus = json.optString("currentStatus").ifBlank { json.optString("status") }.ifBlank { null },
            currentStationName = json.optString("currentStation").ifBlank { json.optString("currentStationName") }.ifBlank { null },
            lastUpdate = json.optString("lastUpdate").ifBlank { json.optString("updatedAt") }.ifBlank { json.optString("lastUpdated") }.ifBlank { null },
            nextStationName = json.optString("nextStation").ifBlank { json.optString("nextStationName") }.ifBlank { null },
            platform = json.optString("platform").ifBlank { null },
            stops = stops,
            fetchedAtMillis = nowMillis,
            viaCache = viaCache,
            freshnessLabel = json.optString("freshness").ifBlank { json.optString("age") }.ifBlank { null },
        )
    }
}
