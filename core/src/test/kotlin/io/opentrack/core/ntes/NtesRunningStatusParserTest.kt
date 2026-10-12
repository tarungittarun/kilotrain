package io.opentrack.core.ntes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser's test is the documented NTES `STNS` shape (public clients of the official app's
 * mobile API). This is a real capture-derived schema, not an imagined payload (7.4).
 */
class NtesRunningStatusParserTest {

    private val documentedBody = """
        {"TrainNo":"16228","TrainName":"TLGP MYS EXP",
         "CurrentStationName":"SMET","LUPDT":"Train departed SMET at 22:58",
         "DelayDep":"10 min","DelayArr":"15 min","Platform":"1","NextStationName":"SME",
         "STNS":[
           {"SC":"TLGP","SN":"Talguppa","STA":"20:55","STD":"21:00","ETA":"21:05","ETD":"21:10",
            "DARR":"10","DDEP":"10","PF":"1","DIST":"0","Sr":"1","Halt":"5"},
           {"SC":"SMET","SN":"Shivamogga Town","STA":"22:55","STD":"23:00","ETA":"23:05","ETD":"23:10",
            "DARR":"10","DDEP":"10","PF":"2","DIST":"29","Sr":"2","Halt":"5","CURR":"Y"},
           {"SC":"SME","SN":"Shivamogga H","STA":"23:05","STD":"23:10","ETA":"23:15","ETD":"23:20",
            "DARR":"10","DDEP":"10","DIST":"32","Sr":"3","Halt":"2"}
         ]}
    """.trimIndent()

    @Test
    fun parsesScheduledAgainstActualPerStopFromTheDocumentedShape() {
        val parsed = NtesRunningStatusParser.parse(documentedBody, 1L, false)
        assertTrue(parsed != null)
        val stops = parsed!!.stops
        assertEquals(3, stops.size)
        val current = stops[1]
        assertEquals("SMET", current.stationCode)
        assertEquals("Shivamogga Town", current.stationName)
        assertEquals("22:55", current.scheduledArrival)
        assertEquals("23:00", current.scheduledDeparture)
        assertEquals("23:05", current.actualArrival)
        assertEquals("23:10", current.actualDeparture)
        assertEquals(10, current.arrivalDelayMinutes)
        assertEquals(10, current.departureDelayMinutes)
        assertEquals("2", current.platform)
        assertTrue(current.isCurrentStation)
        assertEquals("16228", parsed.trainNo)
        assertEquals("Train departed SMET at 22:58", parsed.currentStatus)
        assertEquals("10 min", parsed.delayDeparture)
        assertEquals("1", parsed.platform)
    }

    @Test
    fun anUnreportedStopKeepsNullNotZero() {
        val body = """
            {"TrainNo":"1","STNS":[{"SC":"AAA","SN":"A","ETA":"","ETD":"","DARR":"","DDEP":""}]}
        """.trimIndent()
        val parsed = NtesRunningStatusParser.parse(body, 1L, false)
        val stop = parsed!!.stops.single()
        assertNull(stop.actualArrival) // 3.3/3.4: unstated is an em dash, never a zero delay
        assertNull(stop.arrivalDelayMinutes)
    }

    @Test
    fun theOldReferenceShapeIsNotSilentlyAccepted() {
        // the earlier guessed key names must NOT parse - a wrong shape is a stated failure
        assertNull(
            NtesRunningStatusParser.parse(
                """{"trainFullStatus":[{"stnCode":"SMET","schArrTime":"1"}]}""", 1L, false,
            )
        )
    }

    @Test
    fun nonStatusBodyIsNullNotAGuess() {
        assertNull(NtesRunningStatusParser.parse("~~~~~Please try again after some time.", 1L, false))
        assertNull(NtesRunningStatusParser.parse("", 1L, false))
        assertNull(NtesRunningStatusParser.parse("""{"STNS":[]}""", 1L, false))
        assertNull(NtesRunningStatusParser.parse("""{"somethingElse":1}""", 1L, false))
    }

    @Test
    fun aStopWithoutACodeIsNotAccepted() {
        assertNull(
            NtesRunningStatusParser.parse("""{"TrainNo":"1","STNS":[{"SN":"no code"}]}""", 1L, false)
        )
    }
}
