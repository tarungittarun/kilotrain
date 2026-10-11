package in.opentrack.core.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteStopDelayTest {

    private fun prov() = Provenance(ProvenanceKind.LIVE, "NTES", 1L)

    /** 10.3: due 23:58, seen 00:02 -> 4 minutes LATE, never 14 h early. */
    @Test
    fun due2358Seen0002IsFourMinutesLate() {
        val stop = RouteStop(
            stationCode = "SMET",
            scheduledMinutesOfDay = 23 * 60 + 58,
            actualMinutesOfDay = 0 * 60 + 2,
            platform = null,
            dayOffset = 1,
            provenance = prov(),
        )
        assertEquals(4, stop.delayMinutes())
    }

    @Test
    fun unreportedActualIsNullNotZero() {
        val stop = RouteStop(
            stationCode = "SMET",
            scheduledMinutesOfDay = 17 * 60 + 45,
            actualMinutesOfDay = null,
            platform = null,
            dayOffset = 0,
            provenance = prov(),
        )
        assertNull(stop.delayMinutes())
        assertEquals(null, stop.delayMinutes()) // no delay=0 for an unreported time (3.4)
    }

    @Test
    fun sameDayLateIsSubtracted() {
        val stop = RouteStop(
            stationCode = "NDLS",
            scheduledMinutesOfDay = 8 * 60,
            actualMinutesOfDay = 8 * 60 + 37,
            platform = "5",
            dayOffset = 0,
            provenance = prov(),
        )
        assertEquals(37, stop.delayMinutes())
        assertEquals("5", stop.platformOrNull())
    }

    /** 3.3/3.4: a platform no source states stays null so the UI draws the em dash. */
    @Test
    fun unstatedPlatformIsNull() {
        val stop = RouteStop(
            stationCode = "SMET",
            scheduledMinutesOfDay = 60,
            actualMinutesOfDay = 61,
            platform = null,
            dayOffset = 0,
            provenance = prov(),
        )
        assertNull(stop.platformOrNull())
    }

    /** 10.3: day-2 arrival - 48 h later than schedule is 2 days late, in minutes. */
    @Test
    fun dayTwoArrivalKeepsMinutePrecision() {
        val stop = RouteStop(
            stationCode = "MAS",
            scheduledMinutesOfDay = 22 * 60,
            actualMinutesOfDay = 10,
            platform = null,
            dayOffset = 2,
            provenance = prov(),
        )
        assertEquals(2 * 24 * 60 + 10 - 22 * 60, stop.delayMinutes())
    }
}
