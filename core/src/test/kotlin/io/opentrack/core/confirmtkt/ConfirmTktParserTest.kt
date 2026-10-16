package io.opentrack.core.confirmtkt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmTktParserTest {

    private val body = """
        {"trainNo":"16227","currentStatus":"Running","currentStation":"SMET",
         "stops":[
           {"code":"SMET","name":"Shivamogga Town","schArr":"22:55","schDep":"23:00",
            "actArr":"23:05","actDep":"23:10","delayArr":"10","delayDep":"12","platform":"2",
            "current":"true","distance":"0","haltMins":"5"},
           {"code":"SME","name":"Shivamogga H","schArr":"23:05","schDep":"23:10",
            "actArr":"","actDep":"","delayArr":"","delayDep":"","platform":"2",
            "current":"false","distance":"29","haltMins":"2"}
         ]}
    """.trimIndent()

    @Test
    fun parsesPerStopActuals() {
        val parsed = ConfirmTktParser.parse(body, 1L, false)
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
        val parsed = ConfirmTktParser.parse(body, 1L, false)
        assertNotNull(parsed)
        assertNull(parsed!!.stops[1].actualArrival)
        assertNull(parsed.stops[1].actualDeparture)
    }

    @Test
    fun emptyBodyIsNull() {
        assertNull(ConfirmTktParser.parse("", 1L, false))
        assertNull(ConfirmTktParser.parse("{}", 1L, false))
    }
}
