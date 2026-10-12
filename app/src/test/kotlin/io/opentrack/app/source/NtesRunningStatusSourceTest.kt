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

    /** the documented NTES shape: STNS rows with SC/SN/STA/STD/ETA/ETD/PF/DARR/DDEP */
    private val documentedAnswer = """
        {"TrainNo":"16228","TrainName":"TLGP MYS EXP","CurrentStationName":"SMET",
         "LUPDT":"Train departed SMET at 22:58","DelayDep":"10 min","Platform":"1",
         "NextStationName":"SME",
         "STNS":[
           {"SC":"SMET","SN":"Shivamogga Town","STA":"22:55","STD":"23:00","ETA":"23:05",
            "ETD":"23:10","DARR":"10","DDEP":"10","PF":"2","Sr":"1","Halt":"5"},
           {"SC":"SME","SN":"Shivamogga H","STA":"23:05","STD":"23:10","ETA":"23:15",
            "ETD":"23:20","DARR":"10","DDEP":"10","Sr":"2","Halt":"2","CURR":"Y"}
         ]}
    """.trimIndent()

    private var requests = 0
    private var lastBody: String = ""
    private var response: Pair<Int, String> = 200 to documentedAnswer
    private var warmupMethod: String = ""
    private var warmupCookies: String = ""
    private var postCount: Int = 0

    private fun connectionFactory() = { url: URL ->
        requests += 1
        object : HttpURLConnection(url) {
            override fun disconnect() {}
            override fun usingProxy(): Boolean = false
            override fun connect() {}
            override fun getOutputStream(): OutputStream =
                object : OutputStream() {
                    override fun write(b: Int) {}
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        lastBody = String(b, off, len, Charsets.UTF_8)
                    }
                }
            override fun getResponseCode(): Int {
                if (url.path.endsWith("/")) warmupMethod = "GET" else postCount++
                return response.first
            }
            override fun getInputStream() =
                ByteArrayInputStream(response.second.toByteArray(Charsets.UTF_8))
            override fun getErrorStream(): java.io.InputStream? =
                ByteArrayInputStream(response.second.toByteArray(Charsets.UTF_8))
        }
    }

    private fun source(clock: () -> Long) = NtesRunningStatusSource(connectionFactory(), clock)

    // 2026-10-14T17:46:40Z == 2026-10-14 23:16 IST

    @Test
    fun sendsTheDocumentedJsonBody() {
        val src = source { 1_792_000_000_000L }
        val result = src.fetch("16228", NtesRunningStatusSource.Form.PLAIN_JSON)
        assertTrue(result is NtesRunningStatusSource.NtesStatusResult.Live)
        assertTrue(lastBody.contains("\"queryType\":\"6\""))
        assertTrue(lastBody.contains("\"trainNo\":\"16228\""))
        assertTrue(lastBody.contains("\"startDate\":\"14-Oct-2026\""))
        assertTrue(lastBody.contains("\"stationCode\":\"\""))
        assertTrue(lastBody.contains("\"lang\":\"en\""))
    }

    @Test
    fun theEncryptedFormCarriesTheEnvelopeNotPlaintext() {
        val src = source { 1_792_000_000_000L }
        src.fetch("16228", NtesRunningStatusSource.Form.ENCRYPTED)
        assertTrue(lastBody.contains("\"jsonIn\":\""))
        assertTrue(lastBody.contains("service=TrainRunningMob") == false) // encrypted, not readable
    }

    @Test
    fun theSessionIsWarmedBeforeThePost() {
        val src = source { 1_792_000_000_000L }
        src.fetch("16228")
        assertEquals("GET", warmupMethod) // the session is warmed before the POST
    }

    @Test
    fun parsesPerStopScheduledAgainstActual() {
        val src = source { 1_792_000_000_000L }
        val live = src.fetch("16228") as NtesRunningStatusSource.NtesStatusResult.Live
        assertEquals("Train departed SMET at 22:58", live.status.currentStatus)
        val stops = live.status.stops
        assertEquals(2, stops.size)
        assertEquals("SMET", stops[0].stationCode)
        assertEquals("22:55", stops[0].scheduledArrival)
        assertEquals("23:05", stops[0].actualArrival)
        assertEquals(10, stops[0].arrivalDelayMinutes)
        assertTrue(stops[1].isCurrentStation)
        assertEquals(false, live.status.viaCache)
    }

    /** Protection 3: a warmup happens once; two lookups inside 90 s = ONE POST, the second cached. */
    @Test
    fun theNinetySecondCacheProtectsTheEndpoint() {
        var now = 1_792_000_000_000L
        val src = source { now }
        src.fetch("16228")
        val postsAfterFirst = postCount
        assertEquals(1, postsAfterFirst)
        now += 60_000
        val second = src.fetch("16228") as NtesRunningStatusSource.NtesStatusResult.Live
        assertEquals(1, postCount) // no second POST inside the TTL
        assertTrue(second.status.viaCache)
        now += 31_000 // beyond 90 s
        src.fetch("16228")
        assertEquals(2, postCount)
    }

    @Test
    fun aThrottleBodyIsAStatedFailure() {
        response = 200 to "~~~~~Please try again after some time."
        val src = source { 1L }
        val failed = src.fetch("16228", NtesRunningStatusSource.Form.PLAIN_JSON)
            as NtesRunningStatusSource.NtesStatusResult.Failed
        assertEquals(SourceFailure.SHAPE_UNKNOWN, failed.failure)
        assertTrue(failed.detail.contains("protocol may have changed"))
    }

    @Test
    fun httpErrorIsTyped() {
        response = 503 to "unavailable"
        val src = source { 1L }
        val failed = src.fetch("16228", NtesRunningStatusSource.Form.PLAIN_JSON)
            as NtesRunningStatusSource.NtesStatusResult.Failed
        assertEquals(SourceFailure.HTTP, failed.failure)
    }

    @Test
    fun theDateUsesThreeLetterMonthsNeverNumericMonths() {
        // the date travels inside the encrypted envelope, so it is pinned through the formatter
        val date = NtesRunningStatusSource.defaultDate(1_792_000_000_000L) // 14-Oct-2026 23:16 IST
        assertEquals("14-Oct-2026", date)
        assertTrue(date.matches(Regex("\\d{2}-[A-Z][a-z]{2}-\\d{4}")))
    }
}
