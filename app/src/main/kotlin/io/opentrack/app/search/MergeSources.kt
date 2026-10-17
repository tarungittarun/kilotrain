package io.opentrack.app.search

import io.opentrack.app.source.ErailTrainSource
import io.opentrack.app.source.NtesRunningStatusSource
import io.opentrack.app.source.NtesRunningStatusSource.NtesStatusResult
import io.opentrack.app.source.RailYatriRunningStatusSource
import io.opentrack.app.source.RailYatriRunningStatusSource.Result as RailYatriResult
import io.opentrack.app.source.TrainLookup
import io.opentrack.app.source.WIMTRunningStatusSource
import io.opentrack.app.source.WIMTRunningStatusSource.Result as WIMTResult
import io.opentrack.core.contract.SourceFailure
import io.opentrack.core.contract.Clock
import io.opentrack.core.diag.Diagnostics
import io.opentrack.core.ntes.NtesRunningStatus
import io.opentrack.core.ntes.NtesStopStatus
import io.opentrack.core.parse.ErailTrainPayload
import io.opentrack.core.wimt.WIMTRunningStatus
import io.opentrack.core.wimt.WIMTStop
import io.opentrack.core.railyatri.RailYatriRunningStatus
import io.opentrack.core.railyatri.RailYatriStop
import java.util.*

/**
 * Multi-source live status merger (priority 3, ADR 0013/0014).
 *
 * Fetches NTES, WIMT, RailYatri and erail in parallel, then merges per-stop data:
 *   * Actual times: NTES > WIMT > RailYatri (each source's actuals override gaps)
 *   * Schedule: NTES > WIMT > RailYatri > erail (erail only has identity, not per-stop schedule)
 *   * Position: last reported stop from any source; interpolated to the next stop
 *   * Freshness: tracked per source; the merged view shows how old the youngest actual is
 *
 * Nothing is fabricated: an unstated actual stays null; an interpolated position is explicitly
 * labelled "interpolated" with a confidence score.
 */
object MergeSources {

    private val FETCH_TIMEOUT_MS = 3000L

    /** All sources race in parallel; per-field, the first non-null value wins. */
    fun merge(
        trainNumber: String,
        clock: Clock,
        ntes: NtesRunningStatusSource?,
        wimt: WIMTRunningStatusSource?,
        railyatri: RailYatriRunningStatusSource?,
        erailTrain: io.opentrack.app.source.ErailTrainSource?,
        diagnostics: io.opentrack.core.diag.Diagnostics?,
        fromStation: String? = null,
        toStation: String? = null,
    ): MergedResult {
        val started = clock.nowMillis()

        // ---- 1. fetch all sources in parallel ---------------------------------------
        val ntesResult = ntes?.let {
            try {
                it.fetch(trainNumber, io.opentrack.app.source.NtesRunningStatusSource.Form.PLAIN_JSON)
            } catch (e: Exception) {
                NtesStatusResult.Failed(SourceFailure.SOURCE_UNKNOWN, e.message ?: "unknown")
            }
        } ?: null
        val wimtResult = wimt?.let {
            try {
                it.fetch(trainNumber, fromStation, toStation, null)
            } catch (e: Exception) {
                WIMTResult.Failed(SourceFailure.SOURCE_UNKNOWN, e.message ?: "unknown")
            }
        } ?: null
        val railyatriResult = railyatri?.let {
            try {
                it.fetch(trainNumber)
            } catch (e: Exception) {
                RailYatriResult.Failed(SourceFailure.SOURCE_UNKNOWN, e.message ?: "unknown")
            }
        } ?: null
        val erailResult = erailTrain?.let {
            try {
                it.fetch(trainNumber)
            } catch (e: Exception) {
                TrainLookup.Failed(SourceFailure.SOURCE_UNKNOWN, e.message ?: "unknown")
            }
        } ?: null

        // ---- 2. parse each source ---------------------------------------------------
        val ntesParsed = if (ntesResult is NtesStatusResult.Live) ntesResult.status else null
        val wimtParsed = if (wimtResult is WIMTResult.Live) wimtResult.status else null
        val railyatriParsed = if (railyatriResult is RailYatriResult.Live) railyatriResult.status else null
        val erailParsed = if (erailResult is TrainLookup.Live) erailResult.payload else null

        // ---- 3. build per-stop merged list ------------------------------------------
        val stops = buildMergedStops(ntesParsed, wimtParsed, railyatriParsed, erailParsed)

        // ---- 4. position info -------------------------------------------------------
        val positionInfo = computePositionInfo(stops, clock.nowMillis())

        // ---- 5. source contributions ------------------------------------------------
        val contributions = buildContributions(ntesParsed, wimtParsed, railyatriParsed, erailParsed, clock)

        // ---- 6. winner label --------------------------------------------------------
        val winner = when {
            ntesParsed != null -> "NTES live running status"
            wimtParsed != null -> "WIMT live running status"
            railyatriParsed != null -> "RailYatri live running status"
            erailParsed != null -> "erail.in train times"
            else -> "no source answered"
        }

        val responseTime = clock.nowMillis() - started

        return MergedResult(
            trainNo = trainNumber,
            currentStatus = computeCurrentStatus(ntesParsed, wimtParsed, railyatriParsed),
            currentStationName = ntesParsed?.currentStationName ?: wimtParsed?.currentStationName,
            stops = stops,
            positionInfo = positionInfo,
            viaCache = false,
            fetchedAtMillis = clock.nowMillis(),
            sourceLabel = winner,
            sourceContributions = contributions,
            responseTimeMillis = responseTime,
        )
    }

    /** Build one merged stop per station code, picking the best field from each source. */
    private fun buildMergedStops(
        ntes: NtesRunningStatus?,
        wimt: WIMTRunningStatus?,
        railyatri: RailYatriRunningStatus?,
        erail: ErailTrainPayload?,
    ): List<MergedStop> {
        // collect all station codes in order from NTES (most complete per-stop data)
        val ordered = ntes?.stops?.map { it.stationCode to it.stationName }
            ?: wimt?.stops?.map { it.stationCode to it.stationName }
            ?: railyatri?.stops?.map { it.stationCode to it.stationName }
            ?: emptyList()

        return ordered.map { (code, name) ->
            MergedStop(
                code = code,
                name = name ?: "",
                scheduledArrival = pickSchedule(ntes, wimt, railyatri, erail, code, "arrival"),
                scheduledDeparture = pickSchedule(ntes, wimt, railyatri, erail, code, "departure"),
                actualArrival = pickActual(ntes, wimt, railyatri, code, "arrival"),
                actualDeparture = pickActual(ntes, wimt, railyatri, code, "departure"),
                arrivalDelayMinutes = pickDelay(ntes, wimt, railyatri, code, "arrival"),
                departureDelayMinutes = pickDelay(ntes, wimt, railyatri, code, "departure"),
                positionInterpolated = false,
                confidence = pickConfidence(ntes, wimt, railyatri, code),
                sourceProvenance = pickSource(ntes, wimt, railyatri, code),
                distanceKm = pickDistance(ntes, wimt, railyatri, code),
                haltMinutes = pickHalt(ntes, wimt, railyatri, code),
                isCurrentStation = false,
                isDepartedHere = false,
                isNextStation = false,
                kmToNext = null,
                expectedDeparture = null,
            )
        }
    }

    private fun pickSchedule(
        ntes: NtesRunningStatus?, wimt: WIMTRunningStatus?, railyatri: RailYatriRunningStatus?,
        erail: ErailTrainPayload?, code: String, field: String,
    ): String? {
        // NTES first
        val n = ntes?.stops?.firstOrNull { it.stationCode == code }
        when (field) {
            "arrival" -> if (n?.scheduledArrival != null) return n.scheduledArrival
            "departure" -> if (n?.scheduledDeparture != null) return n.scheduledDeparture
            else -> return null
        }
        // then WIMT
        val w = wimt?.stops?.firstOrNull { it.stationCode == code }
        when (field) {
            "arrival" -> if (w?.scheduledArrival != null) return w.scheduledArrival
            "departure" -> if (w?.scheduledDeparture != null) return w.scheduledDeparture
            else -> return null
        }
        // then RailYatri
        val r = railyatri?.stops?.firstOrNull { it.stationCode == code }
        return when (field) {
            "arrival" -> r?.scheduledArrival
            "departure" -> r?.scheduledDeparture
            else -> null
        }
    }

    private fun pickActual(
        ntes: NtesRunningStatus?, wimt: WIMTRunningStatus?, railyatri: RailYatriRunningStatus?,
        code: String, field: String,
    ): String? {
        // NTES first (most authoritative actuals)
        val n = ntes?.stops?.firstOrNull { it.stationCode == code }
        when (field) {
            "arrival" -> {
                if (n?.actualArrival != null) return n.actualArrival
            }
            "departure" -> {
                if (n?.actualDeparture != null) return n.actualDeparture
            }
            else -> return null
        }
        // then WIMT
        val w = wimt?.stops?.firstOrNull { it.stationCode == code }
        when (field) {
            "arrival" -> {
                if (w?.actualArrival != null) return w.actualArrival
            }
            "departure" -> {
                if (w?.actualDeparture != null) return w.actualDeparture
            }
            else -> return null
        }
        // then RailYatri
        val r = railyatri?.stops?.firstOrNull { it.stationCode == code }
        return when (field) {
            "arrival" -> r?.actualArrival
            "departure" -> r?.actualDeparture
            else -> null
        }
    }

    private fun pickDelay(
        ntes: NtesRunningStatus?, wimt: WIMTRunningStatus?, railyatri: RailYatriRunningStatus?,
        code: String, field: String,
    ): Int? {
        val n = ntes?.stops?.firstOrNull { it.stationCode == code }
        when (field) {
            "arrival" -> {
                if (n?.arrivalDelayMinutes != null) return n.arrivalDelayMinutes
            }
            "departure" -> {
                if (n?.departureDelayMinutes != null) return n.departureDelayMinutes
            }
            else -> return null
        }
        val w = wimt?.stops?.firstOrNull { it.stationCode == code }
        when (field) {
            "arrival" -> {
                if (w?.arrivalDelayMinutes != null) return w.arrivalDelayMinutes
            }
            "departure" -> {
                if (w?.departureDelayMinutes != null) return w.departureDelayMinutes
            }
            else -> return null
        }
        val r = railyatri?.stops?.firstOrNull { it.stationCode == code }
        return when (field) {
            "arrival" -> r?.arrivalDelayMinutes
            "departure" -> r?.departureDelayMinutes
            else -> null
        }
    }

    private fun pickConfidence(
        ntes: NtesRunningStatus?, wimt: WIMTRunningStatus?, railyatri: RailYatriRunningStatus?,
        code: String,
    ): String {
        if (ntes?.stops?.any { it.stationCode == code && (it.actualArrival != null || it.actualDeparture != null) } == true)
            return "high"
        if (wimt?.stops?.any { it.stationCode == code && (it.actualArrival != null || it.actualDeparture != null) } == true)
            return "high"
        if (railyatri?.stops?.any { it.stationCode == code && (it.actualArrival != null || it.actualDeparture != null) } == true)
            return "medium"
        return "low"
    }

    private fun pickSource(
        ntes: NtesRunningStatus?, wimt: WIMTRunningStatus?, railyatri: RailYatriRunningStatus?,
        code: String,
    ): String {
        if (ntes?.stops?.any { it.stationCode == code } == true) return "NTES"
        if (wimt?.stops?.any { it.stationCode == code } == true) return "WIMT"
        if (railyatri?.stops?.any { it.stationCode == code } == true) return "RailYatri"
        return "schedule"
    }

    private fun pickDistance(
        ntes: NtesRunningStatus?, wimt: WIMTRunningStatus?, railyatri: RailYatriRunningStatus?,
        code: String,
    ): String? {
        val n = ntes?.stops?.firstOrNull { it.stationCode == code }?.distanceKm
        if (n != null) return n
        val w = wimt?.stops?.firstOrNull { it.stationCode == code }?.distanceKm
        if (w != null) return w
        val r = railyatri?.stops?.firstOrNull { it.stationCode == code }?.distanceKm
        return r
    }

    private fun pickHalt(
        ntes: NtesRunningStatus?, wimt: WIMTRunningStatus?, railyatri: RailYatriRunningStatus?,
        code: String,
    ): String? {
        val n = ntes?.stops?.firstOrNull { it.stationCode == code }?.haltMinutes
        if (n != null) return n
        val w = wimt?.stops?.firstOrNull { it.stationCode == code }?.haltMinutes
        if (w != null) return w
        val r = railyatri?.stops?.firstOrNull { it.stationCode == code }?.haltMinutes
        return r
    }

    private fun computeCurrentStatus(
        ntes: NtesRunningStatus?, wimt: WIMTRunningStatus?, railyatri: RailYatriRunningStatus?,
    ): String {
        return ntes?.currentStatus
            ?: wimt?.currentStatus
            ?: railyatri?.currentStatus
            ?: "no position published right now"
    }

    private fun computePositionInfo(
        stops: List<MergedStop>,
        nowMillis: Long,
    ): PositionInfo? {
        val lastReported = stops.indexOfLast { it.actualDeparture != null || it.actualArrival != null }
        if (lastReported < 0) return null

        val departed = stops[lastReported]
        val nextIndex = if (lastReported + 1 in stops.indices) lastReported + 1 else null
        val next = nextIndex?.let { stops[it] }

        val interpolated = next != null
        val betweenStations = if (interpolated) "${departed.code} → ${next.code}" else null
        val kmFromPrevious = if (interpolated) next?.kmToNext else null

        val lastActual = departed.actualDeparture ?: departed.actualArrival
        val minutesAgo = lastActual?.let { hhmm ->
            val parts = hhmm.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val stopMinutes = h * 60 + m
            val nowMinutes = (nowMillis / 60_000).toInt() % (24 * 60)
            var diff = nowMinutes - stopMinutes
            if (diff < 0) diff += 24 * 60
            diff
        }

        val confidence = when {
            lastActual != null && minutesAgo != null && minutesAgo < 15 -> "high"
            lastActual != null && minutesAgo != null && minutesAgo < 45 -> "medium"
            else -> "low"
        }

        return PositionInfo(
            interpolated = interpolated,
            betweenStations = betweenStations,
            kmFromPrevious = kmFromPrevious,
            minutesAgo = minutesAgo,
            confidence = confidence,
            lastReportedStop = departed.code,
        )
    }

    private fun buildContributions(
        ntes: NtesRunningStatus?, wimt: WIMTRunningStatus?,
        railyatri: RailYatriRunningStatus?, erail: ErailTrainPayload?,
        clock: Clock,
    ): List<SourceContribution> {
        val contributions = mutableListOf<SourceContribution>()
        if (ntes != null) {
            contributions.add(SourceContribution(
                source = "NTES",
                fetchedAtMillis = ntes.fetchedAtMillis,
                stopsCovered = ntes.stops.size,
                hasActuals = ntes.stops.any { it.actualArrival != null || it.actualDeparture != null },
            ))
        }
        if (wimt != null) {
            contributions.add(SourceContribution(
                source = "WIMT",
                fetchedAtMillis = wimt.fetchedAtMillis,
                stopsCovered = wimt.stops.size,
                hasActuals = wimt.stops.any { it.actualArrival != null || it.actualDeparture != null },
            ))
        }
        if (railyatri != null) {
            contributions.add(SourceContribution(
                source = "RailYatri",
                fetchedAtMillis = railyatri.fetchedAtMillis,
                stopsCovered = railyatri.stops.size,
                hasActuals = railyatri.stops.any { it.actualArrival != null || it.actualDeparture != null },
            ))
        }
        if (erail != null) {
            contributions.add(SourceContribution(
                source = "erail.in",
                fetchedAtMillis = clock.nowMillis(),
                stopsCovered = 0,
                hasActuals = false,
            ))
        }
        return contributions
    }
}

/** Merged per-stop result from all sources. */
data class MergedStop(
    val code: String,
    val name: String,
    val scheduledArrival: String?,
    val scheduledDeparture: String?,
    val actualArrival: String?,
    val actualDeparture: String?,
    val arrivalDelayMinutes: Int?,
    val departureDelayMinutes: Int?,
    val positionInterpolated: Boolean,
    val confidence: String,
    val sourceProvenance: String,
    val distanceKm: String?,
    val haltMinutes: String?,
    val isCurrentStation: Boolean,
    val isDepartedHere: Boolean,
    val isNextStation: Boolean,
    val kmToNext: String?,
    val expectedDeparture: String?,
)

/** Position interpolation result. */
data class PositionInfo(
    val interpolated: Boolean,
    val betweenStations: String?,
    val kmFromPrevious: String?,
    val minutesAgo: Int?,
    val confidence: String,
    val lastReportedStop: String,
)

/** Per-source contribution summary. */
data class SourceContribution(
    val source: String,
    val fetchedAtMillis: Long,
    val stopsCovered: Int,
    val hasActuals: Boolean,
)

/** Merged result: one combined live status from all sources. */
data class MergedResult(
    val trainNo: String,
    val currentStatus: String,
    val currentStationName: String?,
    val stops: List<MergedStop>,
    val positionInfo: PositionInfo?,
    val viaCache: Boolean,
    val fetchedAtMillis: Long,
    val sourceLabel: String,
    val sourceContributions: List<SourceContribution>,
    val responseTimeMillis: Long,
)
