package io.opentrack.core.railyatri

import io.opentrack.core.ntes.JsonReader
import io.opentrack.core.ntes.JsonValue

object RailYatriParser {
    fun parse(body: String, nowMillis: Long, viaCache: Boolean = false): RailYatriRunningStatus? {
        val json = JsonReader.parse(body.trim().removePrefix("﻿")) as? JsonValue.JsonObject ?: return null
        val stops = mutableListOf<RailYatriStop>()
        (json.getArray("stops") ?: json.getArray("route") ?: json.getArray("stations"))?.let { arr ->
            arr.items.forEach { raw ->
                val s = raw as? JsonValue.JsonObject ?: return@forEach
                stops.add(
                    RailYatriStop(
                        stationCode = s.optString("code").ifBlank { s.optString("stationCode") }.ifBlank { null },
                        stationName = s.optString("name").ifBlank { s.optString("stationName") }.ifBlank { null },
                        scheduledArrival = s.optString("schArr").ifBlank { s.optString("arr") }.ifBlank { null },
                        scheduledDeparture = s.optString("schDep").ifBlank { s.optString("dep") }.ifBlank { null },
                        actualArrival = s.optString("actArr").ifBlank { s.optString("actualArr") }.ifBlank { null },
                        actualDeparture = s.optString("actDep").ifBlank { s.optString("actualDep") }.ifBlank { null },
                        arrivalDelayMinutes = s.optString("delay").toIntOrNull() ?: s.optString("late").toIntOrNull(),
                        departureDelayMinutes = null,
                        platform = s.optString("platform").ifBlank { null },
                        isCurrentStation = s.optString("current").equals("true", true) || s.optString("isCurrent").equals("true", true),
                        distanceKm = s.optString("distance").ifBlank { null },
                        haltMinutes = s.optString("halt").ifBlank { null },
                        isPassed = s.optString("passed").equals("true", true) || s.optString("hasDeparted").equals("true", true),
                    )
                )
            }
        }
        if (stops.isEmpty()) return null
        return RailYatriRunningStatus(
            trainNo = json.optString("trainNo").ifBlank { json.optString("trainNumber") }.ifBlank { null },
            currentStatus = json.optString("status").ifBlank { null },
            currentStationName = json.optString("currentStation").ifBlank { json.optString("current") }.ifBlank { null },
            lastUpdate = json.optString("lastUpdate").ifBlank { json.optString("updatedAt") }.ifBlank { json.optString("lastUpdatedTime") }.ifBlank { null },
            nextStationName = json.optString("nextStation").ifBlank { null },
            platform = json.optString("platform").ifBlank { null },
            position = json.optString("position").ifBlank { json.optString("location") }.ifBlank { null },
            stops = stops,
            fetchedAtMillis = nowMillis,
            viaCache = false,
            freshnessLabel = json.optString("freshness").ifBlank { json.optString("age") }.ifBlank { json.optString("minsAgo") }.ifBlank { null },
            isCrowdsourced = true,
        )
    }
}
