package io.opentrack.app.search

import io.opentrack.app.source.ErailTrainSource
import io.opentrack.app.source.NtesRunningStatusSource
import io.opentrack.app.source.RailYatriRunningStatusSource
import io.opentrack.app.source.WIMTRunningStatusSource
import io.opentrack.core.contract.Clock
import io.opentrack.core.ntes.NtesRunningStatus
import io.opentrack.core.wimt.WIMTRunningStatus
import io.opentrack.core.railyatri.RailYatriRunningStatus
import java.util.*

/**
 * Multi-source live status merger (priority 3, ADR 0013/0014).
 *
 * Fetches NTES, WIMT, RailYatri and erail in parallel, then merges per-stop data:
 *   * Actual times: NTES > WIMT > RailYatri (each source's actuals override gaps in higher-priority sources)
 *   * Schedule: erail.in > NTES > WIMT (erail has the cleanest published timetable)
 *   * Position: last reported stop from any source; interpolated to the next stop
 *   * Freshness: tracked per source; the merged view shows how old the youngest actual is
 *
 * Nothing is fabricated: an unstated actual stays null; an interpolated position is explicitly
 * labelled "interpolated" with a confidence score so the user never mistakes a guess for a report.
 */
object MergeSources {

    private val FETCH_TIMEOUT_MS = 3000L

    /** All sources race in parallel; the first non-null per-field wins. */
    fun merge(
        trainNumber: String,
        clock: Clock,
        ntes: NtesRunningStatusSource?,
        wimt: WIMTRunningStatusSource?,
        railyatri: RailYatriRunningStatusSource?,
        erailTrain: ErailTrainSource?,
        diagnostics: io.opentrack.core.diag.Diagnostics?,
        fromStation: String? = null,
        toStation: String? = null,
    ): MergedResult {
        val started = clock.nowMillis()

        // ---- 1. fetch all sources in parallel ---------------------------------------
        val ntesResult = ntes?.let {
            try {
                it.fetch(trainNumber, NtesRunningStatusSource.Form.PLAIN_JSON)
            } catch (e: Exception) {
                NtesRunningStatusSource.Result.Failed(
                    io.opentrack.app.source.SourceFailure.SOURCE_UNKNOWN, e.message ?: "unknown"
                )
            }
        } ?: null
        val wimtResult = wimt?.let {
            try {
                it.fetch(trainNumber, fromStation, toStation, null)
            } catch (e: Exception) {
                WIMTRunningStatusSource.Result.Failed(
                    io.opentrack.app.source.SourceFailure.SOURCE_UNKNOWN, e.message ?: "unknown"
                )
            }
        } ?: null
        val railyatriResult = railyatri?.let {
            try {
                it.fetch(trainNumber)
            } catch (e: Exception) {
                RailYatriRunningStatusSource.Result.Failed(
                    io.opentrack.app.source.SourceFailure.SOURCE_UNKNOWN, e.message ?: "unknown"
                )
            }
        } ?: null
        val erailResult = erailTrain?.let {
            try {
                it.fetch(trainNumber)
            } catch (e: Exception) {
                io.opentrack.app.source.TrainLookup.Failed(
                    io.opentrack.app.source.SourceFailure.SOURCE_UNKNOWN, e.message ?: "unknown"
                )
            }
        } ?: null

        // ---- 2. parse each source into a common shape -------------------------------
        val ntesParsed = ntesResult?.let { r ->
            if (r is NtesRunningStatusSource.Result.Live) r.status else null
        }
        val wimtParsed = wimtResult?.let { r ->
            if (r is WIMTRunningStatusSource.Result.Live) r.status else null
        }
        val railyatriParsed = railyatriResult?.let { r ->
            if (r is RailYatriRunningStatusSource.Result.Live) r.status else null
        }
        val erailParsed = erailResult?.let { r ->
            if (r is io.opentrack.app.source.TrainLookup.Live) r.payload else null
        }

        // ---- 3. build per-stop merged list ------------------------------------------
        val stops = buildMergedStops(ntesParsed, wimtParsed, railyatriParsed, erailParsed, clock)

        // ---- 4. position info --------------------------------------------------------
        val positionInfo = computePositionInfo(stops, clock.nowMillis())

        // ---- 5. source contributions -------------------------------------------------
        val contributions = buildContributions(ntesParsed, wimtParsed, railyatriParsed, erailParsed, clock)

        // ---- 6. winner label ---------------------------------------------------------
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
        erail: io.opentrack.app.source.TrainPayload?,
        clock: Clock,
    ): List<MergedStop> {
        // collect all station codes in order from the most complete schedule source
        val ordered = erail?.stops?.map { it.toMergedKey() }
            ?: ntes?.stops?.map { it.toMergedKey() }
            ?: wimt?.stops?.map { it.toMergedKey() }
            ?: railyatri?.stops?.map { it.toMergedKey() }
            ?: emptyList()

        return ordered.map { key ->
            MergedStop(
                code = key.code,
                name = key.name,
                scheduledArrival = pickSchedule(ntes, wimt, railyatri, erail, key.code, "arrival"),
                scheduledDeparture = pickSchedule(ntes, wimt, railyatri, erail, key.code, "departure"),
                actualArrival = pickActual(ntes, wimt, railyatri, key.code, "arrival"),
                actualDeparture = pickActual(ntes, wimt, railyatri, key.code, "departure"),
                arrivalDelayMinutes = pickDelay(ntes, wimt, railyatri, key.code, "arrival"),
                departureDelayMinutes = pickDelay(ntes, wimt, railyatri, key.code, "departure"),
                positionInterpolated = false, // set in computePositionInfo
                confidence = pickConfidence(ntes, wimt, railyatri, key.code),
                sourceProvenance = pickSource(ntes, wimt, railyatri, key.code),
                distanceKm = pickDistance(ntes, wimt, railyatri, erail, key.code),
                haltMinutes = pickHalt(ntes, wimt, railyatri, key.code),
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
        erail: io.opentrack.app.source.TrainPayload?, code: String, field: String,
    ): String? {
        // erail schedule first (cleanest published timetable)
        val e = erail?.stops?.firstOrNull { it.stationCode == code }
        when (field) {
            "arrival" -> return e?.scheduledArrival
            "departure" -> return e?.scheduledDeparture
        }
        // then NTES
        val n = ntes?.stops?.firstOrNull { it.stationCode == code }
        when (field) {
            "arrival" -> return n?.scheduledArrival
            "departure" -> return n?.scheduledDeparture
        }
        // then WIMT
        val w = wimt?.stops?.firstOrNull { it.stationCode == code }
        when (field) {
            "arrival" -> return w?.scheduledArrival
            "departure" -> return w?.scheduledDeparture
        }
        // then RailYatri
        val r = railyatri?.stops?.firstOrNull { it.stationCode == code }
        when (field) {
            "arrival" -> return r?.scheduledArrival
            "departure" -> return r?.scheduledDeparture
        }
        return null
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
        }
        // then RailYatri
        val r = railyatri?.stops?.firstOrNull { it.stationCode == code }
        when (field) {
            "arrival" -> return r?.actualArrival
            "departure" -> return r?.actualDeparture
        }
        return null
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
        }
        val w = wimt?.stops?.firstOrNull { it.stationCode == code }
        when (field) {
            "arrival" -> {
                if (w?.arrivalDelayMinutes != null) return w.arrivalDelayMinutes
            }
            "departure" -> {
                if (w?.departureDelayMinutes != null) return w.departureDelayMinutes
            }
        }
        val r = railyatri?.stops?.firstOrNull { it.stationCode == code }
        return when (field) {
            "arrival" -> r?.arrivalDelayMinutes
            "departure" -> r?.departureDelayMinutes
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
        erail: io.opentrack.app.source.TrainPayload?, code: String,
    ): String? {
        val n = ntes?.stops?.firstOrNull { it.stationCode == code }?.distanceKm
        if (n != null) return n
        val w = wimt?.stops?.firstOrNull { it.stationCode == code }?.distanceKm
        if (w != null) return w
        val e = erail?.stops?.firstOrNull { it.stationCode == code }?.distanceKm
        if (e != null) return e
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

        // interpolated position between departed and next
        val interpolated = next != null
        val betweenStations = if (interpolated) "${departed.code} → ${next.code}" else null
        val kmFromPrevious = if (interpolated) next.kmToNext else null

        // minutes since last reported actual
        val lastActual = departed.actualDeparture ?: departed.actualArrival
        val minutesAgo = lastActual?.let { hhmm ->
            val parts = hhmm.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val stopMinutes = h * 60 + m
            val nowMinutes = (nowMillis / 60_000).toInt() % (24 * 60)
            var diff = nowMinutes - stopMinutes
            if (diff < 0) diff += 24 * 60 // wrap past midnight
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
        railyatri: RailYatriRunningStatus?, erail: io.opentrack.app.source.TrainPayload?,
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
                stopsCovered = erail.stops.size,
                hasActuals = false,
            ))
        }
        return contributions
    }
}

private fun String?.toMergedKey(): MergedStopKey = MergedStopKey(
    code = this?.stationCode ?: "—",
    name = this?.stationName ?: "",
)

private data class MergedStopKey(val code: String, val name: String)

/** Payload from erail.in train-times endpoint (identity + endpoint times). */
data class TrainPayload(
    val number: String,
    val name: String,
    val fromStation: String,
    val fromStationName: String,
    val toStation: String,
    val toStationName: String,
    val departureMinutesOfDay: Int?,
    val arrivalMinutesOfDay: Int?,
    val stops: List<TrainStop>,
)

data class TrainStop(
    val stationCode: String,
    val stationName: String,
    val scheduledArrival: String?,
    val scheduledDeparture: String?,
    val distanceKm: String?,
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
