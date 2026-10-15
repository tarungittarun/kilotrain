package io.opentrack.core.confirmtkt

data class ConfirmTktStop(
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

data class ConfirmTktRunningStatus(
    val trainNo: String?,
    val currentStatus: String?,
    val currentStationName: String?,
    val lastUpdate: String?,
    val nextStationName: String?,
    val platform: String?,
    val stops: List<ConfirmTktStop>,
    val fetchedAtMillis: Long,
    val viaCache: Boolean = false,
    val freshnessLabel: String? = null,
)
