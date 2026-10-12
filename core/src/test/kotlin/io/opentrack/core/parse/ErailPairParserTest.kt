package io.opentrack.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Graduation tests (7.2): the captured station-pair payload (2026-10-12) and the single-train
 * payload (2026-10-11) are the parse tests. Nothing here is written against an imagined shape
 * (7.4); every non-family body returns null so the caller can say "the feed may have changed".
 */
class ErailPairParserTest {

    private val pairCapture =
        java.io.File("src/test/resources/erail_pair_smet_mys_capture.txt").readText()
    private val trainCapture =
        java.io.File("src/test/resources/erail_trainno_56269_capture.txt").readText()

    @Test
    fun parsesTheRealStationPairCapture() {
        val response = ErailPairParser.parse(pairCapture)
        checkNotNull(response)
        assertEquals(4, response!!.trains.size)
        val numbers = response.trains.map { it.number }
        assertEquals(listOf("16228", "16205", "16226", "16221"), numbers)
        val first = response.trains.first()
        assertEquals("TLGP MYS EXP", first.name)
        assertEquals("TLGP", first.fromStation)
        assertEquals("MYS", first.toStation)
        assertEquals(23 * 60, first.departureMinutesOfDay)
        assertEquals(8 * 60 + 20, first.arrivalMinutesOfDay)
        assertEquals(9 * 60 + 20, first.durationMinutes)
        assertEquals("1111111", first.runDaysRaw)
    }

    @Test
    fun feedStampIsTheEnquiryDayNotTheDeviceDay() {
        val response = ErailPairParser.parse(pairCapture)
        checkNotNull(response)
        val stamp = response!!.feedStampMillis
        // 2026-10-12 10:33:33 IST = 2026-10-12 05:03:33 UTC
        val expected = ErailTrainParser.daysFromCivil(2026, 10, 12) * 86_400_000L +
            (5 * 60 + 3) * 60_000L + 33_000L
        assertEquals(expected, stamp)
    }

    /** The live pair response contains no retired 0-prefix numbers (3.4: identity is never faked). */
    @Test
    fun livePairResponseHasNoRetiredPrefixNumbers() {
        val response = ErailPairParser.parse(pairCapture)
        checkNotNull(response)
        assertTrue(response!!.trains.none { it.number.startsWith("0") })
    }

    /** The single-train capture is the same family: one-train pair response. */
    @Test
    fun singleTrainCaptureParsesAsAPairResponse() {
        val response = ErailPairParser.parse(trainCapture)
        checkNotNull(response)
        assertEquals(1, response!!.trains.size)
        assertEquals("56269", response.trains.single().number)
        assertEquals("SMET MYS FAST PASS", response.trains.single().name)
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
        val sig = ErailPairParser.signatureOf(pairCapture)
        assertTrue(sig["tilde_count"] as Int > 100)
        assertEquals(4, sig["caret_count"])
        assertEquals(true, sig["has_feed_stamp"])
    }

    private fun <T> checkNotNull(v: T?): T? {
        assertTrue("value must not be null", v != null)
        return v
    }
}
