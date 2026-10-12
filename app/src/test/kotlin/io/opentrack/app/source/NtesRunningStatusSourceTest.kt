package io.opentrack.app.source

import io.opentrack.core.contract.SourceFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 6.3: the documented reference contract, exercised offline with a faked connection - the exact
 * JSON body, the 90-second cache (one request for two calls), and the per-stop parse.
 */
class NtesRunningStatusSourceTest {

    private val documentedAnswer = """
        {"trainNo":"16228","currentStatusStr":"Train is running 9 min late",
         "trainFullStatus":[
           {"stnCode":"SMET","stnName":"Shivamogga Town","schArrTime":"22:55","schDepTime":"23:00",
            "actArrTime":"23:05","actDepTime":"23:10","arrDelay":"10","depDelay":"10","isCurrentStn":"false"},
           {"stnCode":"SME","stnName":"Shivamogga H","schArrTime":"23:05","schDepTime":"23:10",
            "actArrTime":"23:15","actDepTime":"23:20","arrDelay":"10","depDelay":"10","isCurrentStn":"true"}
         ]}
    """.trimIndent()

    private var requests = 0
    private var lastBody: String? = null
    private var lastUserAgent: String? = null

    private fun connection(respond: () -> Pair<Int, String>) = { url: URL ->
        requests += 1
        object : HttpURLConnection(url) {
            override fun disconnect() {}
            override fun usingProxy(): Boolean = false
            private val bytes = respond().let { (status, body) ->
                lastBody = ""
                body.toByteArray()
            }
            private var written: ByteArray = ByteArray(0)
            override fun getOutputStream(): OutputStream =
                object : OutputStream() {
                    override fun write(b: Int) {}
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        written = b.copyOfRange(off, off + len)
                        lastBody = String(written)
                    }
                }
            override fun getResponseCode(): Int = respondCode()
            private fun respondCode(): Int = 200
            override fun getInputStream() = ByteArrayInputStream(bytes)
            override fun getErrorStream(): java.io.InputStream? = null
        }
    }

    private fun source(clock: () -> Long, respond: () -> Pair<Int, String> = { 200 to documentedAnswer }) =
        NtesRunningStatusSource(connection(respond), clock)

    @Test
    fun sendsTheDocumentedJsonBodyAndDalvikAgent() {
        var now = 1_792_000_000_000L
        val src = source({ now })
        val result = src.fetch("16228")
        assertTrue(result is NtesRunningStatusSource.NtesStatusResult.Live)
        val body = org.json.JSONObject(lastBody)
        assertEquals("6", body.getString("queryType"))
        assertEquals("16228", body.getString("trainNo"))
        assertEquals("13-Oct-2026", body.getString("startDate"))
        assertEquals("", body.getString("stationCode"))
        assertEquals("en", body.getString("lang"))
    }

    @Test
    fun parsesPerStopScheduledAgainstActual() {
        val src = source({ 1_792_000_000_000L })
        val live = src.fetch("16228") as NtesRunningStatusSource.NtesStatusResult.Live
        assertEquals("Train is running 9 min late", live.status.currentStatus)
        val stops = live.status.stops
        assertEquals(2, stops.size)
        assertEquals("SMET", stops[0].stationCode)
        assertEquals("22:55", stops[0].scheduledArrival)
        assertEquals("23:05", stops[0].actualArrival)
        assertEquals(10, stops[0].arrivalDelayMinutes)
        assertTrue(stops[1].isCurrentStation)
        assertEquals(false, live.status.viaCache)
    }

    /** Protection 3: two calls inside 90 s = ONE request, and the second is labelled cached. */
    @Test
    fun theNinetySecondCacheProtectsTheEndpoint() {
        var now = 1_792_000_000_000L
        val src = source({ now })
        src.fetch("16228")
        assertEquals(1, requests)
        now += 60_000
        val second = src.fetch("16228") as NtesRunningStatusSource.NtesStatusResult.Live
        assertEquals(1, requests) // no second request
        assertTrue(second.status.viaCache)
        now += 31_000 // beyond 90 s
        src.fetch("16228")
        assertEquals(2, requests)
    }

    @Test
    fun aThrottleBodyIsAStatedFailure() {
        val src = source({ 1L }, { 200 to "~~~~~Please try again after some time." })
        val failed = src.fetch("16228") as NtesRunningStatusSource.NtesStatusResult.Failed
        assertEquals(SourceFailure.SHAPE_UNKNOWN, failed.failure)
        assertTrue(failed.detail.contains("protocol may have changed"))
    }

    @Test
    fun httpErrorAndOfflineAreTyped() {
        val http = source({ 1L }, { 503 to "unavailable" })
        assertTrue((http.fetch("1") as NtesRunningStatusSource.NtesStatusResult.Failed).failure
            == SourceFailure.HTTP)
    }

    @Test
    fun theDateUsesThreeLetterMonthsNeverNumericMonths() {
        var now = 1_792_000_000_000L // 2026-10-13T05:46:40Z -> IST 11:16 13-Oct-2026
        val src = source({ now })
        src.fetch("16228")
        val date = org.json.JSONObject(lastBody).getString("startDate")
        assertTrue(date.matches(Regex("\\d{2}-[A-Z][a-z]{2}-\\d{4}")))
        assertEquals("13-Oct-2026", date)
    }
}
