package io.opentrack.core.ntes

/**
 * The documented live-status fields (same captured shape the public NTES clients return). Only
 * these are read; the per-stop ACTUAL array inside the same response is NOT parsed because no
 * capture of it exists here - the app logs its structure for the parser instead of guessing
 * (7.4). Unstated fields stay null and render as an em dash (3.3).
 */
data class NtesLiveStatus(
    val trainNo: String?,
    val trainName: String?,
    val currentStation: String?,
    val currentStationName: String?,
    val lastUpdate: String?,
    val delayDeparture: String?,
    val delayArrival: String?,
    val platform: String?,
    val nextStation: String?,
    val nextStationName: String?,
)

object NtesLiveStatusParser {

    /** null = the decoded body is not the documented shape (caller: SHAPE_UNKNOWN). */
    fun parse(json: String): NtesLiveStatus? {
        val body = trimmed(json)
        if (!body.startsWith("{")) return null
        return NtesLiveStatus(
            trainNo = str(body, "TrainNo"),
            trainName = str(body, "TrainName"),
            currentStation = str(body, "CurrentStation"),
            currentStationName = str(body, "CurrentStationName"),
            lastUpdate = str(body, "LastUpdate"),
            delayDeparture = str(body, "DelayDep"),
            delayArrival = str(body, "DelayArr"),
            platform = str(body, "Platform"),
            nextStation = str(body, "NextStationCode"),
            nextStationName = str(body, "NextStationName"),
        )
    }

    /** The captured shape has TrainNo/TrainName; without them it is not a status (never fabricate). */
    fun looksLikeStatus(status: NtesLiveStatus): Boolean =
        !status.trainNo.isNullOrBlank() && !status.trainName.isNullOrBlank()

    private fun str(body: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    private fun trimmed(json: String) = json.trim().removePrefix("﻿")
}
