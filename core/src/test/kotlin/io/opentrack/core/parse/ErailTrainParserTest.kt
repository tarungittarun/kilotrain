package io.opentrack.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Graduation test (7.2): the probe's captured payload is the parser's test. The fixture is the
 * exact body captured 2026-10-11 from erail.in (attributed in the file header of the probe
 * fixture; small and redacted per 3.5).
 */
class ErailTrainParserTest {

    private val capture = java.io.File("src/test/resources/erail_trainno_56269_capture.txt").readText()

    @Test
    fun parsesTheCapturedPayload() {
        val payload = ErailTrainParser.parse(capture)
        checkNotNull(payload)
        assertEquals("56269", payload.number)
        assertEquals("SMET MYS FAST PASS", payload.name)
        assertEquals("SMET", payload.fromStation)
        assertEquals("Shivamogga Town", payload.fromStationName)
        assertEquals("MYS", payload.toStation)
        assertEquals("Mysore Jn", payload.toStationName)
        assertEquals(10 * 60 + 20, payload.departureMinutesOfDay)
        assertEquals(16 * 60 + 30, payload.arrivalMinutesOfDay)
        assertEquals(6 * 60 + 10, payload.durationMinutes)
        assertEquals("1111111", payload.runDaysRaw)
    }

    @Test
    fun feedStampIsParsedAsFixedIST() {
        val payload = ErailTrainParser.parse(capture)
        checkNotNull(payload)
        // 2026-10-06 16:57:24 IST = 2026-10-06 11:27:24 UTC
        val expected = ErailTrainParser.daysFromCivil(2026, 10, 6) * 86_400_000L +
            (11 * 60 + 27) * 60_000L + 24_000L
        assertEquals(expected, payload.feedStampMillis)
    }

    /** Round trip: stamp text -> millis -> IST wall text must reproduce the stamp (no sign bugs). */
    @Test
    fun stampRoundTripsThroughMillis() {
        val millis = ErailTrainParser.feedStampToMillis("2026-10-12-10-33-33")!!
        assertEquals("2026-10-12 10:33", ErailTrainParser.formatIstOfDay(millis))
        val early = ErailTrainParser.feedStampToMillis("2026-10-12-2-5-6")!!
        assertEquals("2026-10-12 02:05", ErailTrainParser.formatIstOfDay(early))
        val lateNight = ErailTrainParser.feedStampToMillis("2026-10-12-23-0-0")!!
        assertEquals("2026-10-12 23:00", ErailTrainParser.formatIstOfDay(lateNight))
    }

    @Test
    fun civilFromDaysInvertsDaysFromCivil() {
        for (day in listOf(0L, 20732L, 20737L, 20738L, 19000L, 20000L)) {
            val ymd = ErailTrainParser.civilFromDays(day)
            assertEquals(day, ErailTrainParser.daysFromCivil(ymd[0], ymd[1], ymd[2]))
        }
    }

    @Test
    fun throttleBodyIsShapeUnknownNotAParse() {
        val throttled = "~~~~~Please try again after some time."
        assertNull(ErailTrainParser.parse(throttled))
    }

    @Test
    fun emptyBodyIsShapeUnknown() {
        assertNull(ErailTrainParser.parse(""))
        assertNull(ErailTrainParser.parse("~~~"))
    }

    @Test
    fun timeParsingHandlesErrailDottedFormat() {
        assertEquals(0, ErailTrainParser.parseTime("00.00"))
        assertEquals(23 * 60 + 59, ErailTrainParser.parseTime("23.59"))
        assertNull(ErailTrainParser.parseTime("24.00"))
        assertNull(ErailTrainParser.parseTime("10"))
        assertNull(ErailTrainParser.parseTime(""))
    }

    @Test
    fun civilDayArithmeticMatchesKnownAnchors() {
        assertEquals(0L, ErailTrainParser.daysFromCivil(1970, 1, 1))
        assertEquals(20737L, ErailTrainParser.daysFromCivil(2026, 10, 11)) // a Sunday
        assertEquals(20454L, ErailTrainParser.daysFromCivil(2026, 1, 1))
    }
}
