package io.opentrack.core.ntes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NtesLiveStatusParserTest {

    /** The documented captured example (public NTES client README, attributed 2026). */
    private val documented = """
        {
          "TrainNo": "12301",
          "TrainName": "KOLKATA RAJDHANI",
          "CurrentStation": "CNB",
          "CurrentStationName": "KANPUR CENTRAL",
          "LastUpdate": "Train has departed from KANPUR CENTRAL",
          "DelayDep": "On Time",
          "Platform": "4",
          "NextStationCode": "ALD",
          "NextStationName": "ALLAHABAD JN"
        }
    """.trimIndent()

    @Test
    fun parsesTheDocumentedShape() {
        val status = NtesLiveStatusParser.parse(documented)
        assertTrue(NtesLiveStatusParser.looksLikeStatus(status!!))
        assertEquals("12301", status.trainNo)
        assertEquals("KOLKATA RAJDHANI", status.trainName)
        assertEquals("CNB", status.currentStation)
        assertEquals("KANPUR CENTRAL", status.currentStationName)
        assertEquals("Train has departed from KANPUR CENTRAL", status.lastUpdate)
        assertEquals("On Time", status.delayDeparture)
        assertEquals("4", status.platform)
        assertEquals("ALD", status.nextStation)
        assertEquals("ALLAHABAD JN", status.nextStationName)
    }

    @Test
    fun unstatedFieldStaysNull() {
        val status = NtesLiveStatusParser.parse("""{"TrainNo":"1","TrainName":"X"}""")
        assertNull(status!!.platform) // the UI draws the em dash (3.3)
    }

    @Test
    fun aNonStatusBodyIsNullNotAGuess() {
        assertNull(NtesLiveStatusParser.parse("~~~~~Please try again after some time."))
        assertNull(NtesLiveStatusParser.parse(""))
    }

    @Test
    fun aBodyWithoutIdentityIsNotAStatus() {
        val status = NtesLiveStatusParser.parse("""{"DelayDep":"On Time"}""")
        assertFalse(NtesLiveStatusParser.looksLikeStatus(status!!))
    }
}
