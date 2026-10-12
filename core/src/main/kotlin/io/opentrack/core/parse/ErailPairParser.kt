package io.opentrack.core.parse

/**
 * Parses the CAPTURED S2 erail.in payload family (core/src/test/resources/
 * erail_pair_smet_mys_capture.txt captured 2026-10-12 from
 * `getTrains.aspx?Station_From=SMET&Station_To=MYS&DataSource=AVLSOURCE_IRCTC_NGET&Language=EN&Cache=false`;
 * erail_trainno_56269_capture.txt from the single-train call, captured 2026-10-11). Per KICKOFF
 * 7.2/7.4 the parser is written against real captures only, and every use is guarded: a body that
 * does not match the captured family returns null and the caller says "the feed may have changed"
 * (12.3) - it never guesses.
 *
 * Captured family: a header carrying the enquiry pair and the feed stamp
 * (`~<from>~<fromName>~<to>~<toName>~~<feedStamp>~` or `~~...~~<feedStamp>~`), then one row per
 * train anchored by `^<number>~<name>~<fromName>~<fromCode>~<toName>~<toCode>~<viaName>~<viaCode>~
 * <toName>~<toCode>~<dep HH.MM>~<arr HH.MM>~<dur HH.MM>~<runDays 7 chars>~...`
 */
data class ErailPairResponse(
    val trains: List<ErailTrainPayload>,
    val feedStampMillis: Long,
)

object ErailPairParser {

    private val HEADER = Regex("""^~.*?(\d{4}-\d{1,2}-\d{1,2}-\d{1,2}-\d{1,2}-\d{1,2})~+(.*)$""", RegexOption.DOT_MATCHES_ALL)
    private const val MILLIS_PER_DAY = 86_400_000L
    private const val IST_OFFSET_MILLIS = 5 * 60 * 60 * 1000L + 30 * 60 * 1000L

    /**
     * null = the body is not the captured family (throttle messages, HTML, an emptied payload),
     * which callers map to SHAPE_UNKNOWN. A one-train body parses identically to the
     * train-number capture - that is the test that proves the row layout.
     */
    fun parse(body: String): ErailPairResponse? {
        val trimmed = body.trim()
        val header = HEADER.matchEntire(trimmed) ?: return null
        val stamp = header.groupValues[1]
        val stampMillis = ErailTrainParser.feedStampToMillis(stamp) ?: return null
        val rest = header.groupValues[2]

        // rows are anchored by ^<number>; the header row itself is the first train.
        val rowChunks = rest.split("^").filter { it.isNotBlank() }
        val trains = rowChunks.mapNotNull { chunk ->
            parseRow(chunk, stampMillis)
        }
        if (trains.isEmpty()) return null
        return ErailPairResponse(trains, stampMillis)
    }

    /**
     * One train row in the captured layout; field positions come from the 2026-10-11 capture.
     * The chunk includes the number (the row anchor), so positions are the train-number capture's
     * layout shifted by one: 0=number 1=name 2=fromName 3=fromCode 4=toName 5=toCode
     * 10=dep 11=arr 12=duration 13=runDays.
     */
    internal fun parseRow(chunk: String, stampMillis: Long): ErailTrainPayload? {
        val fields = chunk.split("~")
        if (fields.size < 14) return null
        val number = fields[0].trim()
        if (!Regex("""^\d{5}$""").matches(number)) return null
        val departure = ErailTrainParser.parseTime(fields[10]) ?: return null
        val arrival = ErailTrainParser.parseTime(fields[11]) ?: return null
        val name = fields[1].trim()
        val fromStation = fields[3].trim().uppercase()
        val toStation = fields[5].trim().uppercase()
        if (name.isBlank() || fromStation.isBlank() || toStation.isBlank()) return null
        return ErailTrainPayload(
            number = number,
            name = name,
            fromStationName = fields[2].trim(),
            fromStation = fromStation,
            toStationName = fields[4].trim(),
            toStation = toStation,
            departureMinutesOfDay = departure,
            arrivalMinutesOfDay = arrival,
            durationMinutes = ErailTrainParser.parseTime(fields[12]),
            runDaysRaw = fields[13].trim(),
            feedStampMillis = stampMillis,
            trainId = fields.getOrNull(33)?.trim()?.takeIf { it.isNotBlank() },
            stopCount = fields.getOrNull(28)?.trim()?.toIntOrNull(),
        )
    }

    /** Structural signature only (no values) - the capture mechanism for an unknown shape (7.4). */
    fun signatureOf(body: String): Map<String, Any> = mapOf(
        "line_count" to body.lines().size,
        "tilde_count" to body.count { it == '~' },
        "caret_count" to body.count { it == '^' },
        "has_feed_stamp" to Regex("""\d{4}-\d{1,2}-\d{1,2}-\d{1,2}-\d{1,2}-\d{1,2}""").containsMatchIn(body),
        "bytes" to body.length,
    )
}
