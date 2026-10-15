package io.opentrack.core.ixigo

import io.opentrack.core.ntes.JsonReader
import io.opentrack.core.ntes.JsonValue

object IxigoParser {
    fun parse(body: String, nowMillis: Long, viaCache: Boolean = false): IxigoRunningStatus? {
        val json = JsonReader.parse(body.trim().removePrefix("﻿")) as? JsonValue.JsonObject ?: return null
        val stops = mutableListOf<IxigoStop>()
        (json.getArray("stops") ?: json.getArray("routeStations") ?: json.getArray("stations"))?.let { arr ->
            arr.items.forEach { raw ->
                val s = raw as? JsonValue.JsonObject ?: return@forEach
                stops.add(
                    IxigoStop(
                        stationCode = s.optString("stationCode").ifBlank { s.optString("code") }.ifBlank { null },
                        stationName = s.optString("stationName").ifBlank { s.optString("name") }.ifBlank { null },
                        scheduledArrival = s.optString("schArr").ifBlank { s.optString("sta") }.ifBlank { null },
                        scheduledDeparture = s.optString("schDep").ifBlank { s.optString("std") }.ifBlank { null },
                        actualArrival = s.optString("actArr").ifBlank { s.optString("eta") }.ifBlank { null },
                        actualDeparture = s.optString("actDep").ifBlank { s.optString("etd") }.ifBlank { null },
                        arrivalDelayMinutes = s.optString("delay").toIntOrNull(),
                        departureDelayMinutes = s.optString("depDelay").toIntOrNull(),
                        platform = s.optString("platform").ifBlank { s.optString("pf") }.ifBlank { null },
                        isCurrentStation = s.optString("isCurrent").equals("true", true) || s.optString("current").equals("true", true),
                        distanceKm = s.optString("distance").ifBlank { null },
                        haltMinutes = s.optString("halt").ifBlank { null },
                    )
                )
            }
        }
        if (stops.isEmpty()) return null
        return IxigoRunningStatus(
            trainNo = json.optString("trainNo").ifBlank { json.optString("trainNumber") }.ifBlank { null },
            currentStatus = json.optString("status").ifBlank { null },
            currentStationName = json.optString("currentStation").ifBlank { null },
            lastUpdate = json.optString("lastUpdated").ifBlank { json.optString("updatedAt") }.ifBlank { null },
            nextStationName = json.optString("nextStation").ifBlank { null },
            platform = json.optString("platform").ifBlank { null },
            position = json.optString("position").ifBlank { null },
            stops = stops,
            fetchedAtMillis = nowMillis,
            freshnessLabel = json.optString("freshness").ifBlank { null },
        )
    }
}
