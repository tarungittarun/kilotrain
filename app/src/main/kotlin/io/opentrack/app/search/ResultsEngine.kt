package io.opentrack.app.search

import io.opentrack.app.asset.OfflineAsset
import io.opentrack.app.source.ErailTrainSource
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

    fun lookup(from: String, to: String): Outcome {
        if (!asset.schemaCompatible) {
            return Outcome(UiStateName.PROTOCOL_CHANGED, emptyList(), "offline asset", asset.degradeMessage)
        }
        val cached = store.freshCache(from, to, CACHE_TTL_MS)
        if (cached != null) {
            val rows = renderCachePayload(cached.payload, cached.fetchedAtMillis)
            if (rows != null) {
                return Outcome(UiStateName.CACHED, rows, "offline cache",
                    "cached from an earlier lookup")
            }
            return Outcome(UiStateName.COULD_NOT_LOOK, emptyList(), "offline cache",
                "the rows saved on this phone could not be read")
        }

        val hits = asset.lookupByPair(from, to)
        if (hits.isEmpty()) {
            // NOT an empty answer: an incomplete fallback must not claim "no trains exist" (3.3/3.4).
            return Outcome(
                UiStateName.OFFLINE_UNVERIFIED, emptyList(), null,
                "this pair is not in the offline fallback data; that is not proof that no trains run today",
            )
        }
        val rows = hits.map { t ->
            RowView(
                number = t.number,
                name = t.name,
                timeDue = t.departure.ifBlank { null },
                provenanceLabel = t.toIdentity().provenance.label(clock.nowMillis()),
            )
        }
        store.putCache(from, to, cachePayloadFor(hits), clock.nowMillis())
        return Outcome(
            state = UiStateName.OFFLINE_UNVERIFIED,
            rows = rows,
            bannerSource = "offline fallback",
            note = "unverified 2016-vintage fallback timetable - not the railway's current timetable",
        )
    }

    /** Priority 1+2+3: search by train number. Live S2 first (when its flag is on and it
     * answers), the labelled fallback second - and the fallback NEVER pretends to be live. */
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
        val failureDetail = if (live is TrainLookup.Failed) {
            "live source unreachable: ${live.failure} - ${live.detail}"
        } else null

        val hits = asset.trains.filter { it.number == normalised }
        if (hits.isEmpty()) {
            return Outcome(
                UiStateName.OFFLINE_UNVERIFIED, emptyList(), null,
                listOfNotNull(
                    failureDetail,
                    "no train with that number is in the offline fallback data; that is not proof the number is wrong",
                ).joinToString(". "),
            )
        }
        val rows = hits.map { t ->
            RowView(
                number = t.number,
                name = t.name,
                timeDue = t.departure.ifBlank { null },
                provenanceLabel = t.toIdentity().provenance.label(clock.nowMillis()),
            )
        }
        return Outcome(
            state = UiStateName.OFFLINE_UNVERIFIED,
            rows = rows,
            bannerSource = "offline fallback",
            note = listOfNotNull(
                failureDetail,
                "unverified 2016-vintage fallback timetable - not the railway's current timetable",
            ).joinToString(". "),
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

    private fun renderCachePayload(payload: String, fetchedAtMillis: Long): List<RowView>? = try {
        val obj = JSONObject(payload)
        val rows = obj.optJSONArray("rows") ?: return null
        buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val number = r.optString("number", "")
                val name = r.optString("name", "")
                if (number.isBlank() || name.isBlank()) continue
                val provenance = Provenance(ProvenanceKind.CACHED, "offline cache", fetchedAtMillis)
                add(
                    RowView(
                        number = number,
                        name = name,
                        timeDue = r.optString("departure").takeIf { it.isNotBlank() },
                        provenanceLabel = provenance.label(clock.nowMillis()),
                    )
                )
            }
        }
    } catch (e: Exception) { // named: corrupt cache payload is a read failure, never an empty answer
        null
    }

    private fun cachePayloadFor(hits: List<io.opentrack.app.asset.AssetTrain>): String {
        val arr = org.json.JSONArray()
        hits.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("number", t.number)
                    put("name", t.name)
                    put("departure", t.departure)
                    put("from", t.fromStation)
                    put("to", t.toStation)
                }
            )
        }
        return JSONObject().put("rows", arr).toString()
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
