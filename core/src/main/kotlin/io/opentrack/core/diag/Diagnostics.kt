package io.opentrack.core.diag

/**
 * The event kinds the collector records. One per user-facing operation, so an exported log reads
 * as the story of a session: what was asked, what the wire answered, what the screen then said.
 */
enum class DiagKind {
    DEVICE_INFO, PAIR_SEARCH, NUMBER_SEARCH, ROUTE, SCREEN_STATE, SOURCE_FAILURE, EXPORT
}

/**
 * One recorded fact. Wire-level details are STRUCTURE ONLY (counts, status, elapsed) - never
 * payload values (7.4) - unless [rawBody] is explicitly attached by an opted-in raw-capture
 * toggle, in which case it stays on the device and leaves only when the user exports (3.5/3.6).
 */
data class DiagnosticEvent(
    val ts: Long,
    val kind: DiagKind,
    val detail: Map<String, String> = emptyMap(),
    val rawBody: String? = null,
)

/** The sink the app installs (file, memory, tests). */
fun interface DiagSink {
    fun write(line: String)
}

/**
 * Opt-in collector (3.6): while [enabled] is false it records NOTHING - no buffers, no files.
 * All formatting goes through [DiagnosticsCodec] so the export shape has one definition.
 */
class Diagnostics(private val sink: DiagSink, val enabled: Boolean, private val now: () -> Long) {

    fun record(kind: DiagKind, detail: Map<String, String> = emptyMap(), rawBody: String? = null) {
        if (!enabled) return
        sink.write(DiagnosticsCodec.encode(DiagnosticEvent(now(), kind, detail, rawBody)))
    }

    fun deviceInfo(detail: Map<String, String>) = record(DiagKind.DEVICE_INFO, detail)
    fun pairSearch(detail: Map<String, String>) = record(DiagKind.PAIR_SEARCH, detail)
    fun numberSearch(detail: Map<String, String>) = record(DiagKind.NUMBER_SEARCH, detail)
    fun route(detail: Map<String, String>) = record(DiagKind.ROUTE, detail)
    fun screenState(detail: Map<String, String>) = record(DiagKind.SCREEN_STATE, detail)
    fun sourceFailure(detail: Map<String, String>) = record(DiagKind.SOURCE_FAILURE, detail)
    fun export(detail: Map<String, String>) = record(DiagKind.EXPORT, detail)
}

/** Single definition of the exported line format (NDJSON, one event per line). */
object DiagnosticsCodec {

    fun encode(event: DiagnosticEvent): String = buildString {
        append("{\"ts\":").append(event.ts)
        append(",\"kind\":\"").append(event.kind.name).append("\"")
        append(",\"detail\":{")
        event.detail.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) append(",")
            append("\"").append(escape(k)).append("\":\"").append(escape(v)).append("\"")
        }
        append("}")
        event.rawBody?.let {
            append(",\"raw\":\"").append(escape(it)).append("\"")
        }
        append("}")
    }

    /** Structural-signature helper: counts only, so a log can never leak a timetable value (7.4). */
    fun signature(body: String): Map<String, String> = mapOf(
        "bytes" to body.length.toString(),
        "lines" to body.lines().size.toString(),
        "tildes" to body.count { it == '~' }.toString(),
        "carets" to body.count { it == '^' }.toString(),
        "has_feed_stamp" to Regex("""\d{4}-\d{1,2}-\d{1,2}-\d{1,2}-\d{1,2}-\d{1,2}""")
            .containsMatchIn(body).toString(),
        "throttle_marker" to body.contains("try again", ignoreCase = true).toString(),
    )

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
