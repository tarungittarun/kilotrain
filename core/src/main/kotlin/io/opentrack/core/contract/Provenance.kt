package io.opentrack.core.contract

import kotlin.math.abs

/**
 * The ENTIRE provenance vocabulary (KICKOFF 3.2). One function - [label] - renders it;
 * screens may not compose their own labels.
 */
enum class ProvenanceKind {
    /** scheduled values from a timetable: renders `timetable` */
    TIMETABLE,

    /** second-witness rows: renders `via <source>` */
    VIA_SOURCE,

    /** served from cache: renders `cached <age>` */
    CACHED,

    /** fresh from a live feed: renders `live data <age> old` */
    LIVE,
}

/**
 * A value cannot exist without its source (6.2). [fetchedAtMillis] <= 0 is the LEAST trusted
 * state and NEVER renders as "now"; it renders as plain `timetable` and reports
 * [isLeastTrusted].
 */
data class Provenance(
    val kind: ProvenanceKind,
    val source: String,
    val fetchedAtMillis: Long,
) {
    init {
        require(source.isNotBlank()) { "provenance without a source is not provenance (6.2)" }
    }

    val isLeastTrusted: Boolean get() = fetchedAtMillis <= 0
}

/** Freshness TTL table (6.2). */
object FreshnessTtl {
    const val LIVE_STOPS_MS: Long = 5 * 60 * 1000L
    const val LIVE_PAIR_MS: Long = 10 * 60 * 1000L
    const val LIVE_IDENTITY_MS: Long = 24 * 60 * 60 * 1000L
    const val CACHE_MS: Long = 6 * 60 * 60 * 1000L
    const val ASSET_MS: Long = 90 * 24 * 60 * 60 * 1000L
}

/** Injectable clock so freshness is never judged by a wrong device clock (10.3). */
fun interface Clock {
    fun nowMillis(): Long
}

object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/** THE single label function (3.2). Deterministic, total, unit-tested. */
fun Provenance.label(nowMillis: Long): String = when {
    isLeastTrusted -> "timetable" // never claims freshness
    kind == ProvenanceKind.TIMETABLE -> "timetable"
    kind == ProvenanceKind.VIA_SOURCE -> "via $source"
    kind == ProvenanceKind.CACHED -> "cached ${ageWords(nowMillis - fetchedAtMillis)}"
    else -> "live data ${ageWords(nowMillis - fetchedAtMillis)} old"
}

internal fun ageWords(ageMillis: Long): String {
    val safe = abs(ageMillis) // a clock skew behind the feed is still "old", never negative
    val minutes = safe / 60_000
    return when {
        minutes < 1 -> "under a minute"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h"
    }
}
