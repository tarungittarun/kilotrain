package io.opentrack.core.parse

/**
 * Per-stop SCHEDULE, parsed from the captured TRAINROUTE response (2026-10-12):
 * `/data.aspx?Action=TRAINROUTE&Password=2012&Data1=<trainId>&Data2=EN`.
 *
 * Captured row shape: `^<stopNo>~<code>~<name>~<First|Mid|Last>~<arr HH.MM>~<dep HH.MM>~<day>~
 * <distanceKm>~...~<zone>~<destCode>~<nameHindi>~~<lat>~<lon>~...`
 *
 * Written against that capture only (7.2/7.4); anything else returns null and the caller says the
 * feed may have changed (12.3). Arrival/departure are what the source states - never invented.
 */
data class RouteStopSchedule(
    val stopNumber: Int,
    val stationCode: String,
    val stationName: String,
    val role: String,
    val arrival: String?,
    val departure: String?,
    val dayOffset: Int,
    val distanceKm: Int?,
)

object ErailRouteParser {
    // ten positional tokens per row: n, code, name, then seven more (enough for the shifted
    // first/last-stop layout: role, arr, dep, sentinel, day, distance, halt)
    private val ROW = Regex(
        """\^(\d+)~([A-Z0-9]+)~([^~]*)~([^~]*)~([^~]*)~([^~]*)~([^~]*)~([^~]*)~([^~]*)~([^~]*)~"""
    )

    /**
     * Captured layout is positionally identical for middle stops
     * (`^<n>~<code>~<name>~<arr>~<dep>~<day>~<dist>~...`) and shifts by one for the first/last stop,
     * which carry a role token (`First`/`Last`): `^<n>~<code>~<name>~First~<arr>~<dep>~...`.
     * A `0` in a time position is the source's "not stated" sentinel and renders as null so the UI
     * draws the em dash (3.3) - it is never replaced by an invented time (3.4).
     */
    fun parse(body: String): List<RouteStopSchedule>? {
        val stops = ROW.findAll(body).mapNotNull { m ->
            val code = m.groupValues[2].trim().uppercase()
            val name = m.groupValues[3].trim()
            if (code.isBlank() || name.isBlank()) return@mapNotNull null
            val tokens = m.groupValues
            val hasRole = tokens[4].trim().let { it == "First" || it == "Last" }
            val role = if (hasRole) tokens[4].trim() else ""
            val arrivalIdx = if (hasRole) 5 else 4
            val departureIdx = arrivalIdx + 1
            val dayIdx = departureIdx + 2
            val distanceIdx = departureIdx + 3
            RouteStopSchedule(
                stopNumber = tokens[1].toIntOrNull() ?: return@mapNotNull null,
                stationCode = code,
                stationName = name,
                role = role,
                arrival = timeOrNull(tokens[arrivalIdx]),
                departure = timeOrNull(tokens[departureIdx]),
                dayOffset = tokens[dayIdx].trim().toIntOrNull() ?: 1,
                distanceKm = tokens[distanceIdx].trim().toIntOrNull(),
            )
        }.toList()
        return stops.ifEmpty { null }
    }

    private fun timeOrNull(raw: String): String? =
        raw.trim().takeIf { it.isNotBlank() && it != "0" }
}
