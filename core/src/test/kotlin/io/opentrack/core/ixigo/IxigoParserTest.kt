package io.opentrack.core.ixigo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IxigoParserTest {

    private val body = """
        {"trainNo":"16227","status":"Running","currentStation":"SMET",
         "stops":[
           {"stationCode":"SMET","stationName":"Shivamogga Town","schArr":"22:55","schDep":"23:00",
            "actArr":"23:05","actDep":"23:10","delay":"10","depDelay":"12","platform":"2",
            "isCurrent":"true","distance":"0","halt":"5"},
           {"stationCode":"SME","stationName":"Shivamogga H","schArr":"23:05","schDep":"23:10",
            "actArr":"","actDep":"","delay":"","depDelay":"","platform":"2",
            "isCurrent":"false","distance":"29","halt":"2"}
         ]}
    """.trimIndent()

    @Test
    fun parsesPerStopActuals() {
        val parsed = IxigoParser.parse(body, 1L, false)
        assertNotNull(parsed)
        val stops = parsed!!.stops
        assertEquals(2, stops.size)
        assertEquals("SMET", stops[0].stationCode)
        assertEquals("23:05", stops[0].actualArrival)
        assertEquals("23:10", stops[0].actualDeparture)
        assertEquals(10, stops[0].arrivalDelayMinutes)
        assertEquals(12, stops[0].departureDelayMinutes)
        assertTrue(stops[0].isCurrentStation)
        assertEquals("SMET", parsed.currentStationName)
    }

    @Test
    fun blankActualIsNull() {
        val parsed = IxigoParser.parse(body, 1L, false)
        assertNotNull(parsed)
        assertNull(parsed!!.stops[1].actualArrival)
        assertNull(parsed.stops[1].actualDeparture)
    }

    @Test
    fun emptyBodyIsNull() {
        assertNull(IxigoParser.parse("", 1L, false))
        assertNull(IxigoParser.parse("{}", 1L, false))
    }
}
