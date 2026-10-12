package io.opentrack.core.ntes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser's test is the documented response shape (reference: queryType "6" answer with
 * trainFullStatus). The fixture below is a schema-shape body in that documented layout.
 */
class NtesRunningStatusParserTest {

    private fun body(stops: String, status: String = "Train is running 9 min late") = """
        {"trainNo":"16228","currentStatusStr":"$status",
         "trainFullStatus":[$stops]}
    """.trimIndent()

    private fun stop(
        code: String, name: String, schArr: String = "07:05", schDep: String = "07:10",
        actArr: String = "07:14", actDep: String = "07:20", arrDelay: String = "9",
        depDelay: String = "10", current: Boolean = false,
    ) = """{"stnCode":"$code","stnName":"$name","schArrTime":"$schArr","schDepTime":"$schDep",
         "actArrTime":"$actArr","actDepTime":"$actDep","arrDelay":"$arrDelay",
         "depDelay":"$depDelay","isCurrentStn":$current}"""

    @Test
    fun parsesScheduledAgainstActualPerStop() {
        val parsed = NtesRunningStatusParser.parse(
            body(stop("SMET", "Shivamogga Town", current = false) + "," +
                stop("SME", "Shivamogga H", current = true)),
            nowMillis = 1_700_000_000_000L, viaCache = false,
        )
        assertTrue(parsed != null)
        val stops = parsed!!.stops
        assertEquals(2, stops.size)
        val first = stops.first()
        assertEquals("SMET", first.stationCode)
        assertEquals("Shivamogga Town", first.stationName)
        assertEquals("07:05", first.scheduledArrival)
        assertEquals("07:10", first.scheduledDeparture)
        assertEquals("07:14", first.actualArrival)
        assertEquals("07:20", first.actualDeparture)
        assertEquals(9, first.arrivalDelayMinutes)
        assertEquals(10, first.departureDelayMinutes)
        assertFalse(first.isCurrentStation)
        assertTrue(stops[1].isCurrentStation)
        assertEquals("16228", parsed.trainNo)
        assertEquals("Train is running 9 min late", parsed.currentStatus)
    }

    @Test
    fun unstatedActualStaysNullAndIsNotZeroDelay() {
        val parsed = NtesRunningStatusParser.parse(
            body(stop("MYS", "Mysore Jn", actArr = "", actDep = "", arrDelay = "", depDelay = "")),
            1L, false,
        )
        val stop1 = parsed!!.stops.single()
        assertNull(stop1.actualArrival) // 3.3/3.4: unstated is an em dash, never a zero
        assertNull(stop1.arrivalDelayMinutes)
    }

    @Test
    fun nonStatusBodyIsNullNeverAGuess() {
        assertNull(NtesRunningStatusParser.parse("~~~~~Please try again after some time.", 1L, false))
        assertNull(NtesRunningStatusParser.parse("", 1L, false))
        assertNull(NtesRunningStatusParser.parse("""{"trainFullStatus":[]}""", 1L, false))
        assertNull(NtesRunningStatusParser.parse("""{"somethingElse":1}""", 1L, false))
    }

    @Test
    fun aStopWithoutACodeIsNotAccepted() {
        val parsed = NtesRunningStatusParser.parse(
            """{"trainNo":"1","trainFullStatus":[{"stnName":"no code"}]}""", 1L, false,
        )
        assertNull(parsed)
    }
}
