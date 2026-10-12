package io.opentrack.app.search

import io.opentrack.app.asset.OfflineAsset
import io.opentrack.app.source.ErailPairSource
import io.opentrack.app.source.ErailTrainSource
import io.opentrack.app.source.PairLookupResult
import io.opentrack.app.source.FeatureFlags
import io.opentrack.app.source.TrainLookup
import io.opentrack.app.store.OpenTrackStore
import io.opentrack.core.contract.Clock
import io.opentrack.core.contract.Provenance
import io.opentrack.core.contract.ProvenanceKind
import io.opentrack.core.contract.TrainIdentity
import io.opentrack.core.contract.label
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
    private val erailPairSource: ErailPairSource? = null,
    /** Reserved for the live pair cache (TTL) - unused until a pair source is captured (ADR 0009);
     *  deliberately NOT consulted for pairs today, so years-old rows can never surface as answers. */
    private val store: OpenTrackStore,
    private val clock: Clock,
    private val erailSource: ErailTrainSource? = null,
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
            return Outcome(
                state = UiStateName.LIVE,
                rows = rows,
                bannerSource = response.trains.firstOrNull()?.source ?: "erail.in",
                note = "as of ${formatStamp(response.feedStampMillis)} "
                    + "(${response.trains.size} trains between $from and $to, the feed's own stamp)",
            )
        }
        val failure = live as? PairLookupResult.Failed
        return Outcome(
            state = UiStateName.COULD_NOT_LOOK,
            rows = emptyList(),
            bannerSource = null,
            note = ("could not look up trains between $from and $to"
                + (failure?.let { " (${it.failure}: ${it.detail})" } ?: " (source disabled)")
                + ". No old data is shown in its place."),
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
        val live = erailSource?.takeIf { FeatureFlags.ERAIL_TRAIN_NUMBER }?.fetch(normalised)
        if (live is TrainLookup.Live) {
            val p = live.payload
            val provenance = Provenance(ProvenanceKind.VIA_SOURCE, p.source, p.feedStampMillis)
            return Outcome(
                state = UiStateName.LIVE,
                rows = listOf(
                    RowView(
                        number = p.number,
                        name = p.name,
                        timeDue = p.departureMinutesOfDay?.let { formatMinutes(it) },
                        provenanceLabel = provenance.label(clock.nowMillis()),
                    )
                ),
                bannerSource = p.source,
                note = "as of ${formatStamp(p.feedStampMillis)} (the feed's own stamp, not this phone's clock)",
            )
        }
        val failure = (live as? TrainLookup.Failed)
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

    private fun formatStamp(millis: Long): String {
        val days = Math.floorDiv(millis, 86_400_000L)
        val rem = millis - days * 86_400_000L - (5 * 60 + 30) * 60_000L // back to IST wall clock
        var secsOfDay = rem / 1000
        var d = days
        if (secsOfDay < 0) { secsOfDay += 86_400; d -= 1 }
        val (y, m, day) = civilFromDays(d)
        return "%04d-%02d-%02d %02d:%02d".format(y, m, day, secsOfDay / 3600, (secsOfDay % 3600) / 60)
    }

    private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
        var z = days + 719_468L
        val era = (if (z >= 0) z else z - 146_096) / 146_097
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
        return Triple((if (month <= 2) y + 1 else y).toInt(), month, day)
    }

    data class RowView(
        val number: String,
        val name: String,
        val timeDue: String?,
        val provenanceLabel: String,
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
