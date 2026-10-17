package io.opentrack.core.wimt

/**
 * Per-stop live status from whereismytrain.in (WIMT) cache endpoint.
 * The endpoint returns days_schedule with scheduled + actual times, delays, platform per stop.
 * Every field is what the source published; an unstated one stays null and renders as an em dash.
 */
data class WIMTStop(
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

data class WIMTRunningStatus(
    val trainNo: String?,
    val trainName: String?,
    val currentStatus: String?,
    val currentStationName: String?,
    val lastUpdate: String?,
    val sourceStation: String?,
    val destinationStation: String?,
    val stops: List<WIMTStop>,
    val fetchedAtMillis: Long,
    val viaCache: Boolean = false,
    val isCrowdsourced: Boolean = true,
)
