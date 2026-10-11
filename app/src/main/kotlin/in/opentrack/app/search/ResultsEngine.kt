package in.opentrack.app.search

import in.opentrack.core.contract.Clock
import in.opentrack.core.contract.PairListing
import in.opentrack.core.contract.PairLookup
import in.opentrack.core.contract.Provenance
import in.opentrack.core.contract.ProvenanceKind
import in.opentrack.core.contract.SourceFailure
import in.opentrack.core.contract.label
import in.opentrack.core.parse.ShapeSignature
import in.opentrack.app.asset.OfflineAsset
import in.opentrack.app.store.OpenTrackStore

/**
 * The M1 search path: honest, sourceless-by-design cold start. No source is admitted yet, so
 * the ONLY data on screen is the offline asset, and the only possible states are the asset's
 * freshness or COULD_NOT_LOOK / PROTOCOL_CHANGED when a flag-enabled source misbehaves.
 * Live sources arrive in M2 with a captured payload; until then flags are OFF (12.6).
 */
class ResultsEngine(
    private val asset: OfflineAsset,
    private val store: OpenTrackStore,
    private val clock: Clock,
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
        val listing: PairLookup = if (cached != null) {
            renderFromJson(cached.payload, from, to) ?: return Outcome(
                UiStateName.COULD_NOT_LOOK, emptyList(), "offline cache",
                "the rows on this phone could not be read"
            )
        } else {
            assetRows(from, to)
        }
        return when (listing) {
            is PairLookup.Answered -> {
                val rows = listing.listing.rows.map { identity ->
                    RowView(
                        number = identity.number,
                        name = identity.name,
                        timeDue = null, // no minutes are shipped in M1's honest empty asset
                        provenanceLabel = identity.provenance.label(clock.nowMillis()),
                    )
                }
                if (rows.isEmpty()) {
                    // The asset holds nothing: that is NOT an answer of "no trains exist" (3.3).
                    Outcome(UiStateName.OFFLINE_UNVERIFIED, emptyList(), null,
                        "no admitted source has published data yet; this is not an empty answer")
                } else {
                    Outcome(
                        state = if (cached != null) UiStateName.CACHED else UiStateName.OFFLINE_UNVERIFIED,
                        rows = rows,
                        bannerSource = listing.listing.provenance.source,
                        note = listing.listing.note,
                    )
                }
            }
            is PairLookup.CouldNotLook -> Outcome(
                UiStateName.COULD_NOT_LOOK, emptyList(), "offline cache", listing.failure.detail
            )
        }
    }

    private fun assetRows(from: String, to: String): PairLookup {
        val matching = asset.trains.filter {
            it.fromStation == from && it.toStation == to
        }
        return PairLookup.Answered(
            PairListing(
                fromStation = from,
                toStation = to,
                rows = matching,
                provenance = Provenance(ProvenanceKind.TIMETABLE, "offline asset", 0),
                note = if (matching.isEmpty()) null else "unverified timetable data",
            )
        )
    }

    /** Cache payloads are written by this app only; unreadable ones are a named failure. */
    private fun renderFromJson(payload: String, from: String, to: String): PairLookup? = try {
        val obj = org.json.JSONObject(payload)
        val rows = obj.optJSONArray("rows")
        val identities = buildList {
            if (rows != null) for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                add(
                    in.opentrack.core.contract.TrainIdentity(
                        number = r.optString("number", ""),
                        name = r.optString("name", ""),
                        fromStation = r.optString("from", ""),
                        toStation = r.optString("to", ""),
                        provenance = Provenance(ProvenanceKind.CACHED, "offline cache", 0),
                    )
                )
            }
        }
        PairLookup.Answered(
            PairListing(from, to, identities, Provenance(ProvenanceKind.CACHED, "offline cache", 0))
        )
    } catch (e: Exception) { // named: corrupt cache payload is a PARSE failure, never an empty answer
        null
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
        val CACHE_TTL_MS: Long = 6 * 60 * 60 * 1000L // FreshnessTtl.CACHE_MS
        val SOURCE_FAILURES = SourceFailure.values().toList()

        /** Sources stay off until a capture exists (ADR 0002); the flag defaults honest (12.6). */
        val ENABLED_SOURCES: Map<String, Boolean> = mapOf("s1_ntes" to false, "s2_erail" to false)

        /** Shape guard used before any parse (7.4): throttle-shaped bodies never parse. */
        fun shapeRefusesBody(signature: ShapeSignature): Boolean = !signature.looksLikeTrainsPayload()
    }
}
