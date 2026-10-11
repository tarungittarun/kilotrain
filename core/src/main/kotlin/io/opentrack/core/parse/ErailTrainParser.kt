package io.opentrack.core.parse

/**
 * Parsed shape of the CAPTURED S2 erail.in train-number payload (poc/fixtures/s2_erail/
 * trainno_param.txt, captured 2026-10-11). Per KICKOFF 7.2 the probe's capture graduates into
 * this parser plus its tests; nothing here is written against an imagined payload (7.4).
 *
 * Payload shape (measured): `~~...~~<feedStamp>~^<number>~<name>~<fromName>~<fromCode>~
 * <toName>~<toCode>~...~<dep HH.MM>~<arr HH.MM>~<dur HH.MM>~<runDays 7 chars>~...`
 */
data class ErailTrainPayload(
    val number: String,
    val name: String,
    val fromStation: String,
    val fromStationName: String,
    val toStation: String,
    val toStationName: String,
    val departureMinutesOfDay: Int?,
    val arrivalMinutesOfDay: Int?,
    val durationMinutes: Int?,
    /** raw 7-char 0/1 string as published; interpretation is NOT assumed (3.4). */
    val runDaysRaw: String,
    /** feed stamp parsed from the payload - freshness comes from HERE, never System.now (10.3). */
    val feedStampMillis: Long,
    val source: String = "erail.in",
)

object ErailTrainParser {

    private val HEADER = Regex("""^~+(\d{4}-\d{1,2}-\d{1,2}-\d{1,2}-\d{1,2}-\d{1,2})~+\^(\d{5})~(.*)$""")
    private const val MILLIS_PER_DAY = 86_400_000L
    private const val IST_OFFSET_MILLIS = 5 * 60 * 60 * 1000L + 30 * 60 * 1000L // IST is fixed, no DST (10.3)

    /** null = shape unknown (the caller maps that to SHAPE_UNKNOWN; it never fabricates). */
    fun parse(body: String): ErailTrainPayload? {
        val match = HEADER.matchEntire(body.trim()) ?: return null
        val stamp = match.groupValues[1]
        val number = match.groupValues[2]
        val fields = match.groupValues[3].split("~")
        if (fields.size < 13) return null
        val stampMillis = feedStampToMillis(stamp) ?: return null
        return ErailTrainPayload(
            number = number,
            name = fields[0].trim(),
            fromStationName = fields[1].trim(),
            fromStation = fields[2].trim().uppercase(),
            toStationName = fields[3].trim(),
            toStation = fields[4].trim().uppercase(),
            departureMinutesOfDay = parseTime(fields[9]),
            arrivalMinutesOfDay = parseTime(fields[10]),
            durationMinutes = parseTime(fields[11]),
            runDaysRaw = fields[12].trim(),
            feedStampMillis = stampMillis,
        )
    }

    /** `HH.MM` as erail publishes it -> minutes of day. null when absent or malformed. */
    fun parseTime(raw: String): Int? {
        val parts = raw.trim().split(".")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /**
     * Feed stamp `2026-10-6-16-57-24` -> epoch millis, IST fixed. Implemented with civil-day
     * arithmetic (no java.time: minSdk 24 without desugaring, 9.4).
     */
    fun feedStampToMillis(stamp: String): Long? {
        val parts = stamp.split("-")
        if (parts.size != 6) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        val hour = parts[3].toIntOrNull() ?: return null
        val minute = parts[4].toIntOrNull() ?: return null
        val second = parts[5].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..59) {
            return null
        }
        val epochDay = daysFromCivil(year, month, day)
        // UTC = IST - 5:30 (the stamp is IST wall time; adding would make the feed look NEWER)
        return epochDay * MILLIS_PER_DAY + hour * 3_600_000L + minute * 60_000L + second * 1000L - IST_OFFSET_MILLIS
    }

    /** Days since 1970-01-01 from a proleptic-Gregorian date (Hinnant's algorithm). */
    internal fun daysFromCivil(y: Int, m: Int, d: Int): Long {
        val yy = if (m <= 2) y - 1 else y
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = yy - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }
}
