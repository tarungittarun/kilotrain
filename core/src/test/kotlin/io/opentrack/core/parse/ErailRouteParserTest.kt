package io.opentrack.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 7.2: the captured TRAINROUTE response is the parser's test. */
class ErailRouteParserTest {

    private val capture =
        java.io.File("src/test/resources/erail_route_56269_capture.json").readText()

    @Test
    fun parsesTheCapturedRoute() {
        val stops = ErailRouteParser.parse(capture)
        checkNotNull(stops)
        val list = stops!!
        assertTrue(list.size >= 5)
        val first = list.first()
        assertEquals("SMET", first.stationCode)
        assertEquals("Shivamogga Town", first.stationName)
        assertEquals("First", first.role)
        assertEquals("10.20", first.arrival)
        // the origin's departure is the source's "0" sentinel: unstated, never invented
        assertEquals(null, first.departure)
        val second = list[1]
        assertEquals("SME", second.stationCode)
        assertEquals("10.24", second.arrival)
        assertEquals("10.25", second.departure)
        assertEquals(2, second.stopNumber)
        assertEquals(2, second.distanceKm)
        assertEquals(1, second.dayOffset)
    }

    @Test
    fun distancesAreWhatTheSourceStates() {
        val stops = ErailRouteParser.parse(capture)!!
        assertEquals(0, stops.first().distanceKm)
        assertTrue(stops.any { it.distanceKm != null })
    }

    @Test
    fun dayTwoStopKeepsItsDay() {
        val stops = ErailRouteParser.parse(capture)!!
        assertTrue(stops.any { it.dayOffset == 2 })
    }

    @Test
    fun junkIsNullNotAParse() {
        assertNull(ErailRouteParser.parse("~~~~~Please try again after some time."))
        assertNull(ErailRouteParser.parse(""))
    }

    private fun <T> checkNotNull(v: T?): T? {
        assertTrue("value must not be null", v != null)
        return v
    }
}
