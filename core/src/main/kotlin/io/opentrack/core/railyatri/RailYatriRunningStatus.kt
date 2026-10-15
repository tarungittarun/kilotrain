package io.opentrack.core.railyatri

data class RailYatriStop(
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
    val isPassed: Boolean = false,
)

data class RailYatriRunningStatus(
    val trainNo: String?,
    val currentStatus: String?,
    val currentStationName: String?,
    val lastUpdate: String?,
    val nextStationName: String?,
    val platform: String?,
    val position: String?,
    val stops: List<RailYatriStop>,
    val fetchedAtMillis: Long,
    val viaCache: Boolean = false,
    val freshnessLabel: String? = null,
    val isCrowdsourced: Boolean = true,
)
