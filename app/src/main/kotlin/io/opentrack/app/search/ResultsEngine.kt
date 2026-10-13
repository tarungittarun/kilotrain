package io.opentrack.app.search

import io.opentrack.app.asset.OfflineAsset
import io.opentrack.app.source.ErailPairSource
import io.opentrack.app.source.ErailTrainSource
import io.opentrack.app.source.ErailRouteSource
import io.opentrack.app.source.NtesRunningStatusSource
import io.opentrack.app.source.NtesRunningStatusSource.NtesStatusResult
import io.opentrack.app.source.PairLookupResult
import io.opentrack.app.source.RouteLookupResult
import io.opentrack.core.diag.Diagnostics
import io.opentrack.core.ntes.NtesRunningStatus
import kotlinx.coroutines.runBlocking
import io.opentrack.app.source.FeatureFlags
import io.opentrack.app.source.TrainLookup
import io.opentrack.app.store.OpenTrackStore
import io.opentrack.core.contract.Clock
import io.opentrack.core.contract.Provenance
import io.opentrack.core.contract.ProvenanceKind
import io.opentrack.core.contract.TrainIdentity
import io.opentrack.core.contract.label
import io.opentrack.core.parse.ErailTrainParser
import io.opentrack.core.parse.ShapeSignature
import org.json.JSONObject

/**
 * The M2 search path over the labelled cold-start fallback (ADR 0007): S3 identity data, 2016
 * vintage, permanently labelled `timetable`. Live sources arrive when a captured payload exists
 * (ADR 0002); until then their flags stay OFF (12.6).
 *
 * Honesty rules enforced HERE, not by review:
 *  * 3.3/3.4: a pair ABSENT from the fallback is NOT an empty answer - the fallback is incomplete,
 *    so "no trains today" would be a fabrication. Missing pairs render as OFFLINE_UNVERIFIED.
 *  * 3.2: every row carries its label from the single label() function.
 *  * 10.8: a build stamp is never a data vintage; the asset's measured vintage travels with it.
 */
class ResultsEngine(
    private val asset: OfflineAsset,
    /** Reserved for the live pair cache (TTL) - deliberately NOT consulted for pairs, so
     *  years-old rows can never surface as answers (ADR 0009). */
    private val store: OpenTrackStore,
    private val clock: Clock,
    private val erailSource: ErailTrainSource? = null,
    private val erailPairSource: ErailPairSource? = null,
    private val erailRouteSource: ErailRouteSource? = null,
    private val ntesStatusSource: NtesRunningStatusSource? = null,
    private val diagnostics: Diagnostics? = null,
) {
    data class Outcome(
        val state: UiStateName,
        val rows: List<RowView>,
        val bannerSource: String?,
        val note: String?,
    )

    /**
     * Station-pair search. Rule (ADR 0009, owner 2026-10-12): a pair query is answered ONLY by a
     * current source. The S3 fallback is 2016-vintage identity data - presenting it as an answer
     * to "what runs between TLGP and MYS" would be exactly the defect the owner rejected. So when
     * no current source answers, the screen says so (3.1: "we don't have that feature yet").
     */
    fun lookup(from: String, to: String): Outcome {
        if (!asset.schemaCompatible) {
            return Outcome(UiStateName.PROTOCOL_CHANGED, emptyList(), "offline asset", asset.degradeMessage)
        }
        // The pair query runs on the PASSENGER's device (ADR 0010), so it uses the passenger's
        // network. The cache is NOT consulted: it may hold pre-rule rows (ADR 0009).
        diagnostics?.pairSearch(mapOf("from" to from, "to" to to, "source" to "erail_pair"))
        val live = erailPairSource?.takeIf { FeatureFlags.ERAIL_PAIR }?.fetch(from, to)
        if (live is PairLookupResult.Live) {
            val response = live.response
            val rows = response.trains.map { p ->
                RowView(
                    number = p.number,
                    name = p.name,
                    timeDue = p.departureMinutesOfDay?.let { formatMinutes(it) },
                    provenanceLabel = Provenance(ProvenanceKind.VIA_SOURCE, p.source, p.feedStampMillis)
                        .label(clock.nowMillis()),
                )
            }
            recordOutcome(diagnostics, "pair", from, to, live.wire, UiStateName.LIVE, rows,
                rows.size)
            return Outcome(
                state = UiStateName.LIVE,
                rows = rows,
                bannerSource = response.trains.firstOrNull()?.source ?: "erail.in",
                note = "as of ${ErailTrainParser.formatIstOfDay(response.feedStampMillis)} "
                + "(${response.trains.size} trains between $from and $to, the feed's own stamp)",
            )
        }
        val failure = live as? PairLookupResult.Failed
        recordOutcome(diagnostics, "pair", from, to, failure?.wire, UiStateName.COULD_NOT_LOOK,
            emptyList(), 0, failure?.detail)
        return Outcome(
            state = UiStateName.COULD_NOT_LOOK,
            rows = emptyList(),
            bannerSource = null,
            note = ("could not look up trains between $from and $to"
                + (failure?.let { " (${it.failure}: ${it.detail})" } ?: " (source disabled)")
                + ". No old data is shown in its place."),
        )
    }

    /** One line per completed operation: wire structure + the exact rows the screen will show. */
    private fun recordOutcome(
        diagnostics: Diagnostics?,
        kind: String,
        a: String,
        b: String,
        wire: Map<String, String>?,
        state: UiStateName,
        rows: List<RowView>,
        count: Int,
        failureDetail: String? = null,
    ) {
        diagnostics ?: return
        diagnostics.screenState(
            mapOf(
                "op" to kind,
                "query" to "$a|$b",
                "ui_state" to state.name,
                "rows" to rows.joinToString(", ") { "${it.number}@${it.timeDue ?: "-"}[${it.provenanceLabel}]" },
                "count" to count.toString(),
            ) + (wire ?: emptyMap()) + mapOf("failure" to (failureDetail ?: "-"))
        )
    }

    /**
     * Priority 1+2: train-number search. The live S2 endpoint (ADR 0008) answers when it can;
     * when it cannot, the app says so instead of substituting years-old data (ADR 0009).
     */
    fun lookupByNumber(number: String): Outcome {
        if (!asset.schemaCompatible) {
            return Outcome(UiStateName.PROTOCOL_CHANGED, emptyList(), "offline asset", asset.degradeMessage)
        }
        val normalised = number.trim()
        diagnostics?.numberSearch(mapOf("train_no" to normalised, "source" to "erail_train"))
        val live = erailSource?.takeIf { FeatureFlags.ERAIL_TRAIN_NUMBER }?.fetch(normalised)
        if (live is TrainLookup.Live) {
            val p = live.payload
            val provenance = Provenance(ProvenanceKind.VIA_SOURCE, p.source, p.feedStampMillis)
            val rows = listOf(
                RowView(
                    number = p.number,
                    name = p.name,
                    timeDue = p.departureMinutesOfDay?.let { formatMinutes(it) },
                    provenanceLabel = provenance.label(clock.nowMillis()),
                )
            )
            recordOutcome(diagnostics, "number", p.number, "-", live.wire, UiStateName.LIVE, rows, 1)
            return Outcome(
                state = UiStateName.LIVE,
                rows = rows,
                bannerSource = p.source,
                note = "as of ${ErailTrainParser.formatIstOfDay(p.feedStampMillis)} (the feed's own stamp, not this phone's clock)",
            )
        }
        val failure = (live as? TrainLookup.Failed)
        recordOutcome(diagnostics, "number", normalised, "-", failure?.wire,
            UiStateName.COULD_NOT_LOOK, emptyList(), 0, failure?.detail)
        return Outcome(
            state = UiStateName.COULD_NOT_LOOK,
            rows = emptyList(),
            bannerSource = null,
            note = ("could not reach erail.in for train $normalised"
                + (failure?.let { " (${it.failure}: ${it.detail})" } ?: " (source disabled)")
                + ". No old data is shown in its place."),
        )
    }

    private fun formatMinutes(minutes: Int): String =
        "%02d:%02d".format(minutes / 60, minutes % 60)


    data class RowView(
        val number: String,
        val name: String,
        val timeDue: String?,
        val provenanceLabel: String,
    )

    /** Priority 3 (schedule half): the per-stop schedule, only what the source states. */
    fun lookupRoute(number: String): RouteOutcome {
        val result = erailRouteSource?.fetch(number.trim())
            ?: return RouteOutcome.Unavailable("route source not installed (12.6)")
        diagnostics?.route(mapOf("train_no" to number.trim()))
        return when (result) {
            is RouteLookupResult.Live -> {
                val stops = result.stops.map { stop ->
                    StopView(
                        code = stop.stationCode,
                        name = stop.stationName,
                        arrival = stop.arrival,
                        departure = stop.departure,
                    )
                }
                diagnostics?.route(
                    mapOf(
                        "train_no" to number.trim(),
                        "stops" to stops.joinToString(", ") { "${it.code}@${it.arrival ?: "-"}" },
                        "count" to stops.size.toString(),
                    ) + result.wire
                )
                RouteOutcome.Stops(stops)
            }
            is RouteLookupResult.Failed -> {
                diagnostics?.sourceFailure(
                    mapOf("op" to "route", "failure" to result.failure.name, "detail" to result.detail)
                        + result.wire
                )
                RouteOutcome.Unavailable(
                    "could not get the stop times (${result.failure}: ${result.detail})"
                )
            }
        }
    }

    /**
     * Priority 3 (ADR 0013/0014, first-wins ladder): FIVE named, keyless sources are raced and the
     * FIRST answer the phone receives is displayed:
     *   1. NTES mobile gateway - plain JSON queryType "6"   (per-stop ACTUALS)
     *   2. NTES mobile gateway - encrypted envelope          (per-stop ACTUALS)
     *   3. erail.in TRAINROUTE                              (per-stop SCHEDULE, labelled)
     *   4. erail.in train-number payload                    (identity + endpoint times)
     *   5. the offline asset                                (labelled cold start, no live claim)
     * Every rung's outcome is recorded; the screen names the winner and what the others said.
     */
    fun liveStatus(trainNumber: String): StatusOutcome = runBlocking {
        val train = trainNumber.trim()
        val ntes = ntesStatusSource
        val route = erailRouteSource
        val aggregator = io.opentrack.app.source.FirstWinsAggregator<StatusOutcome>()
        val rungs = listOfNotNull(
            ntes?.let {
                io.opentrack.app.source.FirstWinsAggregator.Rung<StatusOutcome>("NTES (mobile JSON)") {
                    when (val r = it.fetch(train, NtesRunningStatusSource.Form.PLAIN_JSON)) {
                        is NtesStatusResult.Live ->
                            statusFromNtes(r.status, train, "NTES live running status")
                        is NtesStatusResult.Failed -> null
                    }
                }
            },
            ntes?.let {
                io.opentrack.app.source.FirstWinsAggregator.Rung<StatusOutcome>("NTES (encrypted)") {
                    when (val r = it.fetch(train, NtesRunningStatusSource.Form.ENCRYPTED)) {
                        is NtesStatusResult.Live ->
                            statusFromNtes(r.status, train, "NTES live running status")
                        is NtesStatusResult.Failed -> null
                    }
                }
            },
            route?.let {
                io.opentrack.app.source.FirstWinsAggregator.Rung<StatusOutcome>("erail.in schedule") {
                    when (val r = it.fetch(train)) {
                        is RouteLookupResult.Live -> StatusOutcome.Live(
                            trainNo = train,
                            currentStatus = "showing the published schedule - no live source answered first",
                            stops = r.stops.map { stop ->
                                StopStatus(
                                    code = stop.stationCode,
                                    name = stop.stationName,
                                    scheduledArrival = stop.arrival,
                                    scheduledDeparture = stop.departure,
                                    actualArrival = null,
                                    actualDeparture = null,
                                    arrivalDelayMinutes = null,
                                    departureDelayMinutes = null,
                                    isCurrentStation = false,
                                    isTrainHere = false,
                                    expectedDeparture = null,
                                )
                            },
                            viaCache = false,
                            fetchedAtMillis = clock.nowMillis(),
                            sourceLabel = "erail.in schedule",
                            attempts = emptyList(),
                        )
                        is RouteLookupResult.Failed -> null
                    }
                }
            },
            erailSource?.let {
                io.opentrack.app.source.FirstWinsAggregator.Rung<StatusOutcome>("erail.in train times") {
                    when (val r = it.fetch(train)) {
                        is io.opentrack.app.source.TrainLookup.Live -> {
                            val p = r.payload
                            StatusOutcome.Live(
                                trainNo = p.number,
                                currentStatus = "endpoint times - " + p.name,
                                stops = listOf(
                                    StopStatus(
                                        code = p.fromStation,
                                        name = p.fromStationName,
                                        scheduledArrival = p.departureMinutesOfDay?.let { m -> formatMinutes(m) },
                                        scheduledDeparture = null,
                                        actualArrival = null,
                                        actualDeparture = null,
                                        arrivalDelayMinutes = null,
                                        departureDelayMinutes = null,
                                        isCurrentStation = true,
                                        isTrainHere = true,
                                        expectedDeparture = null,
                                    ),
                                    StopStatus(
                                        code = p.toStation,
                                        name = p.toStationName,
                                        scheduledArrival = null,
                                        scheduledDeparture = p.arrivalMinutesOfDay?.let { m -> formatMinutes(m) },
                                        actualArrival = null,
                                        actualDeparture = null,
                                        arrivalDelayMinutes = null,
                                        departureDelayMinutes = null,
                                        isCurrentStation = false,
                                        isTrainHere = false,
                                        expectedDeparture = null,
                                    ),
                                ),
                                viaCache = false,
                                fetchedAtMillis = p.feedStampMillis,
                                sourceLabel = "erail.in train times",
                                attempts = emptyList(),
                            )
                        }
                        is io.opentrack.app.source.TrainLookup.Failed -> null
                    }
                }
            },
            io.opentrack.app.source.FirstWinsAggregator.Rung<StatusOutcome>("offline asset (labelled)") {
                val hit = asset.trains.firstOrNull { it.number == train }
                if (hit != null) StatusOutcome.Live(
                    trainNo = hit.number,
                    currentStatus = "offline fallback - 2016 vintage, unverified",
                    stops = listOf(
                        StopStatus(
                            code = hit.fromStation, name = hit.fromStation, scheduledArrival = hit.departure,
                            scheduledDeparture = null, actualArrival = null, actualDeparture = null,
                            arrivalDelayMinutes = null, departureDelayMinutes = null,
                            isCurrentStation = true, isTrainHere = true, expectedDeparture = null,
                        ),
                    ),
                    viaCache = false,
                    fetchedAtMillis = 0,
                    sourceLabel = "offline asset (unverified)",
                    attempts = emptyList(),
                ) else null
            },
        )
        diagnostics?.numberSearch(
            mapOf("train_no" to train, "source" to "live_status_ladder") +
                mapOf("rungs" to rungs.joinToString(", ") { it.name })
        )
        val outcome = aggregator.race(rungs)
        val winner = outcome.value
        if (winner != null) {
            diagnostics?.numberSearch(
                mapOf("train_no" to train, "source" to "live_status_winner", "winner" to (outcome.winner ?: "?"))
            )
            val winnerLive = winner as? StatusOutcome.Live
            return@runBlocking if (winnerLive != null) {
                winnerLive.copy(
                    attempts = if (outcome.attempts.isEmpty()) listOf("answered first by ${outcome.winner}")
                    else outcome.attempts,
                )
            } else winner
        }
        diagnostics?.sourceFailure(
            mapOf("op" to "live_status", "failure" to "ALL_SOURCES_FAILED",
                "detail" to (outcome.attempts + rungs.map { it.name }).joinToString(" | "))
        )
        val tried = rungs.joinToString(", ") { it.name } +
            if (outcome.attempts.isEmpty()) "" else " | details: " + outcome.attempts.joinToString("; ")
        StatusOutcome.Unavailable("no source answered first. Tried: $tried")
    }

    private fun statusFromNtes(
        status: NtesRunningStatus,
        train: String,
        label: String,
    ): StatusOutcome.Live = StatusOutcome.Live(
        trainNo = status.trainNo ?: train,
        currentStatus = status.currentStatus ?: "no position published right now",
        stops = withPositionAndExpectations(
            status.stops.map { stop ->
                StopStatus(
                    code = stop.stationCode ?: "—",
                    name = stop.stationName ?: "",
                    scheduledArrival = stop.scheduledArrival,
                    scheduledDeparture = stop.scheduledDeparture,
                    actualArrival = stop.actualArrival,
                    actualDeparture = stop.actualDeparture,
                    arrivalDelayMinutes = stop.arrivalDelayMinutes,
                    departureDelayMinutes = stop.departureDelayMinutes,
                    isCurrentStation = stop.isCurrentStation,
                    isTrainHere = false,
                    expectedDeparture = null,
                )
            },
            status.delayDeparture,
        ),
        viaCache = status.viaCache,
        fetchedAtMillis = status.fetchedAtMillis,
        sourceLabel = label,
        attempts = emptyList(),
    )

    /**
     * Position + expectations, computed from what NTES published and nothing else:
     *  * the train's position is the last stop with a reported actual, or the CURR stop;
     *  * stops with no reported actual show an EXPECTED time = schedule + the published delay
     *    (labelled expected, never actual - 3.4).
     */
    internal fun withPositionAndExpectations(
        stops: List<StopStatus>,
        delayDeparture: String?,
    ): List<StopStatus> {
        val delayMinutes = Regex("(\d+)").find(delayDeparture ?: "")?.groupValues?.get(1)?.toIntOrNull()
            ?: if (delayDeparture?.contains("On Time", ignoreCase = true) == true) 0 else null
        val lastReported = stops.indexOfLast { it.actualDeparture != null || it.actualArrival != null }
        val positionIndex = stops.indexOfFirst { it.isCurrentStation }.let { if (it >= 0) it else lastReported }
        return stops.mapIndexed { index, stop ->
            val here = index == positionIndex
            val expected = if (stop.actualDeparture == null && delayMinutes != null) {
                stop.scheduledDeparture?.let { shiftMinutes(it, delayMinutes) }
            } else null
            stop.copy(isTrainHere = here, expectedDeparture = expected)
        }
    }

    /** HH:mm + minutes, wrapping past midnight (10.3). */
    internal fun shiftMinutes(hhmm: String, minutes: Int): String {
        val parts = hhmm.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return hhmm
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return hhmm
        val total = (h * 60 + m + minutes).mod(24 * 60)
        return "%02d:%02d".format(total / 60, total % 60)
    }

    data class StopStatus(
        val code: String,
        val name: String,
        val scheduledArrival: String?,
        val scheduledDeparture: String?,
        val actualArrival: String?,
        val actualDeparture: String?,
        val arrivalDelayMinutes: Int?,
        val departureDelayMinutes: Int?,
        val isCurrentStation: Boolean,
        /** the train's position as published: the last stop with a reported actual, or CURR. */
        val isTrainHere: Boolean,
        /** schedule + the published delay, for stops with no reported actual (labelled expected). */
        val expectedDeparture: String?,
    )

    sealed interface StatusOutcome {
        data class Live(
            val trainNo: String,
            val currentStatus: String,
            val stops: List<StopStatus>,
            val viaCache: Boolean,
            val fetchedAtMillis: Long,
            val sourceLabel: String,
            val attempts: List<String>,
        ) : StatusOutcome
        data class Unavailable(val reason: String) : StatusOutcome
    }

    sealed interface RouteOutcome {
        data class Stops(val stops: List<StopView>) : RouteOutcome
        data class Unavailable(val reason: String) : RouteOutcome
    }

    data class StopView(
        val code: String,
        val name: String,
        val arrival: String?,
        val departure: String?,
    )

    enum class UiStateName {
        LIVE, LIVE_STALE, CACHED, OFFLINE_UNVERIFIED, ANSWERED_EMPTY, COULD_NOT_LOOK, PARTIAL, PROTOCOL_CHANGED
    }

    companion object {
        val CACHE_TTL_MS: Long = io.opentrack.core.contract.FreshnessTtl.CACHE_MS
        val SOURCE_FAILURES = io.opentrack.core.contract.SourceFailure.values().toList()

        /** Sources stay off until a capture exists (ADR 0002); the flag defaults honest (12.6). */
        val ENABLED_SOURCES: Map<String, Boolean> = mapOf("s1_ntes" to false, "s2_erail" to false)

        /** Shape guard used before any parse (7.4): throttle-shaped bodies never parse. */
        fun shapeRefusesBody(signature: ShapeSignature): Boolean = !signature.looksLikeTrainsPayload()

        /** Identity must never be cleaned up into something the railway does not use (3.4). */
        fun namePreserved(raw: String): String = raw.trim()

        /** 10.3: 4-letter codes are never truncated anywhere in this path. */
        fun codePreserved(code: String): String = code.trim().uppercase()
    }
}
