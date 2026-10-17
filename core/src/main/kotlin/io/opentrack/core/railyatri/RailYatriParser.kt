package io.opentrack.core.railyatri

import io.opentrack.core.ntes.JsonReader
import io.opentrack.core.ntes.JsonValue

object RailYatriParser {
    fun parse(body: String, nowMillis: Long, viaCache: Boolean = false): RailYatriRunningStatus? {
        val json = JsonReader.parse(body.trim().removePrefix("﻿")) as? JsonValue.JsonObject ?: return null
        // livestatus.railyatri.in may return train-level info without a stops array.
        // Parse what we have: current station, next station, platform, status.
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
        val trainNo = json.optString("trainNumber").ifBlank { json.optString("trainNo") }.ifBlank { null }
        val currentStatus = json.optString("status").ifBlank { json.optString("title") }.ifBlank { json.optString("new_message") }.ifBlank { null }
        val currentStationName = json.optString("currentStation").ifBlank { json.optString("source_stn_name") }.ifBlank { json.optString("current") }.ifBlank { null }
        val lastUpdate = json.optString("lastUpdate").ifBlank { json.optString("updatedAt") }.ifBlank { json.optString("notification_date") }.ifBlank { null }
        val nextStationName = json.optString("nextStation").ifBlank { json.optString("next_station_name") }.ifBlank { null }
        val platform = json.optString("platform").ifBlank { json.optString("platform_number") }.ifBlank { null }
        val position = json.optString("position").ifBlank { json.optString("location") }.ifBlank { null }
        return RailYatriRunningStatus(
            trainNo = trainNo,
            currentStatus = currentStatus,
            currentStationName = currentStationName,
            lastUpdate = lastUpdate,
            nextStationName = nextStationName,
            platform = platform,
            position = position,
            stops = stops,
            fetchedAtMillis = nowMillis,
            viaCache = false,
            freshnessLabel = json.optString("freshness").ifBlank { json.optString("age") }.ifBlank { json.optString("minsAgo") }.ifBlank { null },
            isCrowdsourced = true,
        )
    }
}
