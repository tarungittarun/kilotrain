package io.opentrack.core.wimt

import io.opentrack.core.ntes.JsonReader
import io.opentrack.core.ntes.JsonValue

object WIMTParser {
    fun parse(body: String, nowMillis: Long, viaCache: Boolean = false): WIMTRunningStatus? {
        val json = JsonReader.parse(body.trim().removePrefix("﻿")) as? JsonValue.JsonObject ?: return null
        val stops = mutableListOf<WIMTStop>()
        (json.getArray("days_schedule") ?: json.getArray("stops") ?: json.getArray("route"))?.let { arr ->
            arr.items.forEach { raw ->
                val s = raw as? JsonValue.JsonObject ?: return@forEach
                stops.add(
                    WIMTStop(
                        stationCode = s.optString("station_code").ifBlank { null },
                        stationName = s.optString("station_name").ifBlank { null },
                        scheduledArrival = s.optString("sch_arrival_time").ifBlank { null },
                        scheduledDeparture = s.optString("sch_departure_time").ifBlank { null },
                        actualArrival = s.optString("actual_arrival_time").ifBlank { null },
                        actualDeparture = s.optString("actual_departure_time").ifBlank { null },
                        arrivalDelayMinutes = s.optString("delay_in_arrival").toIntOrNull(),
                        departureDelayMinutes = s.optString("delay_in_departure").toIntOrNull(),
                        platform = s.optString("platform").ifBlank { null },
                        isCurrentStation = s.optString("curStn").equals("true", true),
                        distanceKm = s.optString("distance").ifBlank { null },
                        haltMinutes = null,
                    )
                )
            }
        }
        if (stops.isEmpty()) return null
        return WIMTRunningStatus(
            trainNo = null,
            trainName = json.optString("train_name").ifBlank { null },
            currentStatus = null,
            currentStationName = json.optString("curStn").ifBlank { null },
            lastUpdate = json.optString("lastUpdateIsoDate").ifBlank { null },
            sourceStation = json.optString("source_station").ifBlank { null },
            destinationStation = json.optString("destination_station").ifBlank { null },
            stops = stops,
            fetchedAtMillis = nowMillis,
            viaCache = viaCache,
            isCrowdsourced = true,
        )
    }
}
