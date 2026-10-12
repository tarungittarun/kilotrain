package io.opentrack.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Graduation test (7.2): the captured train-number payload IS a one-train member of the same
 * family the pair endpoint serves, so it is the pair parser's test. Anything that does not match
 * the family returns null and the app says "the feed may have changed" (12.3).
 */
class ErailPairParserTest {

    private val capture = java.io.File("src/test/resources/erail_trainno_56269_capture.txt").readText()

    @Test
    fun capturedOneTrainBodyParsesAsAPairResponse() {
        val response = ErailPairParser.parse(capture)
        requireNotNull(response)
        assertEquals(1, response!!.trains.size)
        val t = response.trains.single()
        assertEquals("56269", t.number)
        assertEquals("SMET MYS FAST PASS", t.name)
        assertEquals("SMET", t.fromStation)
        assertEquals("MYS", t.toStation)
        assertEquals(620, t.departureMinutesOfDay)
        assertEquals(990, t.arrivalMinutesOfDay)
        assertEquals(370, t.durationMinutes)
        assertEquals("1111111", t.runDaysRaw)
        assertEquals(t.feedStampMillis, response.feedStampMillis)
    }

    @Test
    fun stampMatchesTheTrainNumberParser() {
        val single = ErailTrainParser.parse(capture)
        val pair = ErailPairParser.parse(capture)
        requireNotNull(single); requireNotNull(pair)
        assertEquals(single!!.feedStampMillis, pair!!.feedStampMillis)
    }

    @Test
    fun throttleBodyIsNullNotAParse() {
        assertNull(ErailPairParser.parse("~~~~~Please try again after some time."))
    }

    @Test
    fun htmlBodyIsNullNotAParse() {
        assertNull(ErailPairParser.parse("<!DOCTYPE html><html><body>erail</body></html>"))
        assertNull(ErailPairParser.parse(""))
    }

    @Test
    fun signatureCountsStructureWithoutValues() {
        val sig = ErailPairParser.signatureOf(capture)
        assertTrue(sig["tilde_count"] as Int > 0)
        assertEquals(true, sig["has_feed_stamp"])
        assertTrue(sig["bytes"] as Int > 0)
    }

    private fun <T> requireNotNull(v: T?): T? {
        assertTrue("value must not be null", v != null)
        return v
    }
}
