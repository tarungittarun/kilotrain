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
    private val ROW = Regex("""\^(\d+)~([A-Z0-9]+)~([^~]*)~([^~]*)~([^~]*)~([^~]*)~(\d+)~([^~]*)~""")

    fun parse(body: String): List<RouteStopSchedule>? {
        val stops = ROW.findAll(body).mapNotNull { m ->
            val code = m.groupValues[2].trim().uppercase()
            val name = m.groupValues[3].trim()
            if (code.isBlank() || name.isBlank()) return@mapNotNull null
            RouteStopSchedule(
                stopNumber = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null,
                stationCode = code,
                stationName = name,
                role = m.groupValues[4].trim(),
                arrival = m.groupValues[5].trim().takeIf { it.isNotBlank() },
                departure = m.groupValues[6].trim().takeIf { it.isNotBlank() },
                dayOffset = m.groupValues[7].toIntOrNull() ?: 1,
                distanceKm = m.groupValues[8].trim().toIntOrNull(),
            )
        }.toList()
        return stops.ifEmpty { null }
    }
}
