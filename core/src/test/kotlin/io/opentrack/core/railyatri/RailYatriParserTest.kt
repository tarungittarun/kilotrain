package io.opentrack.core.railyatri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RailYatriParserTest {

    private val body = """
        {"trainNo":"16227","status":"Running","currentStation":"SMET",
         "stops":[
           {"code":"SMET","stationName":"Shivamogga Town","schArr":"22:55","schDep":"23:00",
            "actArr":"23:05","actDep":"23:10","delay":"10","platform":"2","current":"true",
            "distance":"0","halt":"5","passed":"false"},
           {"code":"SME","stationName":"Shivamogga H","schArr":"23:05","schDep":"23:10",
            "actArr":"","actDep":"","delay":"","platform":"2","current":"false",
            "distance":"29","halt":"2","passed":"false"}
         ]}
    """.trimIndent()

    @Test
    fun parsesPerStopActuals() {
        val parsed = RailYatriParser.parse(body, 1L, false)
        assertNotNull(parsed)
        val stops = parsed!!.stops
        assertEquals(2, stops.size)
        assertEquals("SMET", stops[0].stationCode)
        assertEquals("23:05", stops[0].actualArrival)
        assertEquals("23:10", stops[0].actualDeparture)
        assertEquals(10, stops[0].arrivalDelayMinutes)
        assertTrue(stops[0].isCurrentStation)
        assertFalse(stops[0].isPassed)
        assertEquals("SMET", parsed.currentStationName)
    }

    @Test
    fun blankActualIsNullNotZero() {
        val parsed = RailYatriParser.parse(body, 1L, false)
        assertNotNull(parsed)
        assertNull(parsed!!.stops[1].actualArrival)
        assertNull(parsed.stops[1].actualDeparture)
    }

    @Test
    fun emptyBodyIsNull() {
        assertNull(RailYatriParser.parse("", 1L, false))
        assertNull(RailYatriParser.parse("{}", 1L, false))
    }
}
