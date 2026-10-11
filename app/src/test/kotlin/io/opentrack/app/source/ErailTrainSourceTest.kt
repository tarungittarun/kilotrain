package io.opentrack.app.source

import io.opentrack.core.contract.SourceFailure
import io.opentrack.core.parse.ErailTrainParser
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 6.3: transport is injected, so fetch+parse are testable offline with the real capture. */
class ErailTrainSourceTest {

    private val capture = java.io.File("src/test/resources/erail_trainno_56269_capture.txt").readText()

    private fun transport(body: String?, status: Int = 200, throws: IOException? = null) =
        ErailTransport { _ ->
            if (throws != null) throw throws
            TransportReply(status, body)
        }

    @Test
    fun capturedPayloadBecomesALiveLookup() {
        val result = ErailTrainSource(transport(capture)).fetch("56269")
        assertTrue(result is TrainLookup.Live)
        val live = result as TrainLookup.Live
        assertEquals("56269", live.payload.number)
        assertEquals("SMET MYS FAST PASS", live.payload.name)
        assertEquals("SMET", live.payload.fromStation)
        assertEquals("MYS", live.payload.toStation)
        assertEquals(620, live.payload.departureMinutesOfDay)
        assertEquals(990, live.payload.arrivalMinutesOfDay)
        assertEquals("1111111", live.payload.runDaysRaw)
    }

    @Test
    fun throttleBodyIsShapeUnknownNotATrain() {
        val result = ErailTrainSource(transport("~~~~~Please try again after some time.")).fetch("56269")
        assertTrue(result is TrainLookup.Failed)
        assertEquals(SourceFailure.SHAPE_UNKNOWN, (result as TrainLookup.Failed).failure)
    }

    @Test
    fun httpErrorIsTyped() {
        val result = ErailTrainSource(transport("nope", status = 503)).fetch("56269")
        assertEquals(SourceFailure.HTTP, (result as TrainLookup.Failed).failure)
    }

    @Test
    fun emptyBodyIsEMPTYNotALateAnswer() {
        val result = ErailTrainSource(transport("")).fetch("56269")
        assertEquals(SourceFailure.EMPTY, (result as TrainLookup.Failed).failure)
    }

    @Test
    fun timeoutIsTypedTIMEOUT() {
        val result = ErailTrainSource(
            transport(null, throws = SocketTimeoutException("read timed out"))
        ).fetch("56269")
        assertEquals(SourceFailure.TIMEOUT, (result as TrainLookup.Failed).failure)
    }

    @Test
    fun offlineIsTypedOFFLINE() {
        val result = ErailTrainSource(
            transport(null, throws = IOException("airplane mode"))
        ).fetch("56269")
        assertEquals(SourceFailure.OFFLINE, (result as TrainLookup.Failed).failure)
    }

    @Test
    fun disabledFlagIsAStatedReasonNotASilentEmpty() {
        val result = ErailTrainSource(transport(capture), enabled = false).fetch("56269")
        assertTrue(result is TrainLookup.Failed)
        assertEquals(SourceFailure.OFFLINE, (result as TrainLookup.Failed).failure)
    }

    @Test
    fun theCapturedPayloadStillParsesThroughTheSourcePath() {
        val parsed = ErailTrainParser.parse(capture)
        requireNotNull(parsed)
        assertEquals(56269L, parsed!!.number.toLong())
    }

    private fun <T> requireNotNull(v: T?): T? {
        assertTrue("value must not be null", v != null)
        return v
    }
}
