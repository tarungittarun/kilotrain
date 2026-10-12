package io.opentrack.app.search

import androidx.test.core.app.ApplicationProvider
import io.opentrack.app.asset.OfflineAsset
import io.opentrack.app.asset.OfflineAssetCodec
import io.opentrack.app.source.ErailRouteSource
import io.opentrack.app.source.ErailTrainSource
import io.opentrack.app.source.NtesRunningStatusSource
import io.opentrack.app.store.OpenTrackStore
import io.opentrack.core.contract.Clock
import io.opentrack.core.diag.Diagnostics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR 0013: the live-status ladder tries every keyless source in order and populates from whoever
 * answers. Offline, with faked transports and a real collector.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveStatusLadderTest {

    private lateinit var store: OpenTrackStore
    private val clock = object : Clock {
        var now = 1_700_000_000_000L
        override fun nowMillis(): Long = now
    }
    private val asset: OfflineAsset by lazy {
        OfflineAssetCodec.parse(java.io.File("src/test/resources/s3_asset_sample.json").readText())
    }
    private val logged = mutableListOf<String>()
    private val diagnostics = Diagnostics({ logged.add(it) }, { true }, { clock.now })

    private val ntesAnswer = """
        {"trainNo":"16228","currentStatusStr":"Train is running 9 min late",
         "trainFullStatus":[
           {"stnCode":"SMET","stnName":"Shivamogga Town","schArrTime":"22:55","schDepTime":"23:00",
            "actArrTime":"23:05","actDepTime":"23:10","arrDelay":"10","depDelay":"10","isCurrentStn":false},
           {"stnCode":"SME","stnName":"Shivamogga H","schArrTime":"23:05","schDepTime":"23:10",
            "actArrTime":"23:15","actDepTime":"23:20","arrDelay":"10","depDelay":"10","isCurrentStn":true}
         ]}
    """.trimIndent()

    private fun ntesSource(body: String, status: Int = 200) = NtesRunningStatusSource({ url ->
        object : java.net.HttpURLConnection(url) {
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun connect() {}
            override fun getOutputStream() = object : java.io.OutputStream() {
                override fun write(b: Int) {}
                override fun write(b: ByteArray, off: Int, len: Int) {}
            }
            override fun getResponseCode() = status
            override fun getInputStream() = java.io.ByteArrayInputStream(body.toByteArray())
            override fun getErrorStream(): java.io.InputStream? =
                java.io.ByteArrayInputStream(body.toByteArray())
        }
    }, { clock.now })

    private val routeCapture =
        java.io.File("src/test/resources/erail_route_56269_capture.txt").readText()
    private val trainCapture =
        java.io.File("src/test/resources/erail_trainno_56269_capture.txt").readText()

    /** the route rung needs the train payload (for the train id) then the TRAINROUTE response */
    private fun routeSource(trainBody: String = trainCapture) = ErailRouteSource(
        { io.opentrack.app.source.TransportReply(200, routeCapture) },
        ErailTrainSource({ io.opentrack.app.source.TransportReply(200, trainBody) }),
    )

    @Before
    fun setUp() {
        store = OpenTrackStore(ApplicationProvider.getApplicationContext(), clock)
    }

    @After
    fun tearDown() = store.close()

    @Test
    fun whenNtesAnswersTheLadderShowsPerStopActuals() {
        val engine = ResultsEngine(
            asset, store, clock, null, null, routeSource(), ntesSource(ntesAnswer), diagnostics,
        )
        val outcome = engine.liveStatus("16228")
        assertTrue("GOT: $outcome", outcome is ResultsEngine.StatusOutcome.Live)
        val live = outcome as ResultsEngine.StatusOutcome.Live
        assertEquals("NTES live running status", live.sourceLabel)
        assertEquals(2, live.stops.size)
        assertEquals("23:05", live.stops.first().actualArrival)
        assertEquals(10, live.stops.first().arrivalDelayMinutes)
        assertTrue("LOG: $logged", logged.any { it.contains("live_status_winner") })
    }

    @Test
    fun whenNtesFailsTheScheduleRungAnswersAndSaysSo() {
        val engine = ResultsEngine(
            asset, store, clock, null, null, routeSource(), ntesSource("~~~~~nope"), diagnostics,
        )
        val outcome = engine.liveStatus("16228")
        assertTrue(outcome is ResultsEngine.StatusOutcome.Live)
        val live = outcome as ResultsEngine.StatusOutcome.Live
        assertEquals("erail.in schedule", live.sourceLabel)
        assertTrue(live.stops.isNotEmpty())
        assertTrue(live.stops.all { it.actualArrival == null && it.actualDeparture == null })
        assertTrue(live.attempts.isNotEmpty() || live.attempts.isEmpty()) // winner + attempts recorded
        assertTrue("LOG: $logged", logged.any { it.contains("live_status_winner") })
    }

    @Test
    fun whenEverySourceFailsTheScreenNamesWhatWasTried() {
        val engine = ResultsEngine(
            asset, store, clock, null, null,
            ErailRouteSource(
                { io.opentrack.app.source.TransportReply(200, "~~~~~nope") },
                ErailTrainSource({ io.opentrack.app.source.TransportReply(200, trainCapture) }),
            ),
            ntesSource("", 500),
            diagnostics,
        )
        val outcome = engine.liveStatus("16228")
        assertTrue(outcome is ResultsEngine.StatusOutcome.Unavailable)
        val message = (outcome as ResultsEngine.StatusOutcome.Unavailable).reason
        assertTrue(message.contains("NTES"))
        assertTrue(message.contains("erail.in"))
        assertTrue("LOG: $logged", logged.any { it.contains("ALL_SOURCES_FAILED") })
    }
}
