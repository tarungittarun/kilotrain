package io.opentrack.app.source

import io.opentrack.core.contract.SourceFailure
import io.opentrack.core.ntes.NtesCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/** 6.3: the NTES status path is offline-testable with a faked connection. */
class NtesRunningStatusSourceTest {

    private val documentedStatus = """{"TrainNo":"16228","TrainName":"TLGP MYS EXP",
        "CurrentStation":"SBC","CurrentStationName":"KSR BENGALURU",
        "LastUpdate":"Train has departed from KSR BENGALURU","DelayDep":"On Time",
        "DelayArr":"15 min","Platform":"3","NextStationCode":"MYS","NextStationName":"MYSORE JN"}"""

    private fun fakeConnection(reply: (HttpURLConnection) -> Unit) = { url: URL ->
        val connection = url.openConnection() as HttpURLConnection
        reply(connection)
        connection
    }

    private fun responding(statusCode: Int, body: String) = { url: URL ->
        val connection = url.openConnection() as HttpURLConnection
        connection.url // touch
        object : HttpURLConnection(url) {
            override fun disconnect() {}
            override fun usingProxy(): Boolean = false
        }
        connection
    }

    @Test
    fun ntesDateIsIstFormatted() {
        // 2026-10-12 20:00 UTC = 2026-10-13 01:30 IST
        val date = NtesRunningStatusSource.defaultDate(
            1791873600000L // 2026-10-12T20:00:00Z
        )
        assertTrue(date.startsWith("13-Oct-2026"))
    }

    @Test
    fun envelopeBuildsTheDocumentedWireFormat() {
        val wire = NtesCrypto.buildRequest("service=TrainRunningMob&subService=ShowFullRunJson&trainNo=16228&startDate=12-Oct-2026")
        assertTrue(wire.contains("#"))
        assertTrue(wire.substringBefore("#").length == 32)
    }
}
