package io.opentrack.core.parse

/**
 * Per-stop SCHEDULE, parsed from the captured TRAINROUTE response (2026-10-12):
 * `/data.aspx?Action=TRAINROUTE&Password=2012&Data1=<trainId>&Data2=EN`.
 *
 * Captured row shape: `^<stopNo>~<code>~<name>~<arr HH.MM>~<dep HH.MM>~...` for middle stops and
 * `^<stopNo>~<code>~<name>~<First|Last>~<arr HH.MM>~<dep HH.MM>~...` for the first/last stop, so
 * the role token shifts the time positions by one. Only these fields are parsed: the rest of the
 * row (day/distance/halt) is left alone rather than guessed at from ambiguous positions.
 *
 * Written against that capture only (7.2/7.4); anything else returns null and the caller says the
 * feed may have changed (12.3). Times are exactly what the source states - a `0` sentinel means
 * "not stated" and becomes null so the UI draws the em dash (3.3), never an invented time (3.4).
 */
data class RouteStopSchedule(
    val stopNumber: Int,
    val stationCode: String,
    val stationName: String,
    val role: String,
    val arrival: String?,
    val departure: String?,
)

object ErailRouteParser {
    // eight positional tokens: stopNo, code, name, then five more (enough for role + both times)
    private val ROW = Regex(
        """\^(\d+)~([A-Z0-9]+)~([^~]*)~([^~]*)~([^~]*)~([^~]*)~([^~]*)~"""
    )

    fun parse(body: String): List<RouteStopSchedule>? {
        val stops = ROW.findAll(body).mapNotNull { m ->
            val code = m.groupValues[2].trim().uppercase()
            val name = m.groupValues[3].trim()
            if (code.isBlank() || name.isBlank()) return@mapNotNull null
            val t = m.groupValues
            val hasRole = t[4].trim().let { it == "First" || it == "Last" }
            val arrivalIdx = if (hasRole) 5 else 4
            RouteStopSchedule(
                stopNumber = t[1].toIntOrNull() ?: return@mapNotNull null,
                stationCode = code,
                stationName = name,
                role = if (hasRole) t[4].trim() else "",
                arrival = timeOrNull(t[arrivalIdx]),
                departure = timeOrNull(t[arrivalIdx + 1]),
            )
        }.toList()
        return stops.ifEmpty { null }
    }

    private fun timeOrNull(raw: String): String? =
        raw.trim().takeIf { it.isNotBlank() && it != "0" }
}
