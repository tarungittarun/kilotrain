package io.opentrack.app.source

import io.opentrack.core.contract.SourceFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 6.3: route fetch is offline-testable with the two captured payloads. */
class ErailRouteSourceTest {

    private val trainCapture =
        java.io.File("src/test/resources/erail_trainno_56269_capture.txt").readText()
    private val routeCapture =
        java.io.File("src/test/resources/erail_route_56269_capture.json").readText()

    private var lastPath: String? = null

    private fun transport(vararg replies: Pair<Int, String>) = ErailTransport { path ->
        lastPath = path
        val (status, body) = replies.first()
        TransportReply(status, body)
    }

    @Test
    fun routeFetchesThroughTheTrainIdFromTheCapturedPayload() {
        // the source reads the payload for the train id, then asks for the route by id
        val source = ErailRouteSource(transport(200 to routeCapture), ErailTrainSource({ TransportReply(200, trainCapture) }))
        val result = source.fetch("56269")
        assertTrue(result is RouteLookupResult.Live)
        val stops = (result as RouteLookupResult.Live).stops
        assertEquals("SMET", stops.first().stationCode)
        assertEquals("10.20", stops.first().arrival)
        assertTrue(lastPath!!.contains("Data1=2322"))
    }

    @Test
    fun aNonFamilyRouteIsAStatedFailure() {
        val source = ErailRouteSource(
            transport(200 to "~~~~~Please try again after some time."),
            ErailTrainSource({ TransportReply(200, trainCapture) }),
        )
        val result = source.fetch("56269")
        assertTrue(result is RouteLookupResult.Failed)
        assertEquals(SourceFailure.SHAPE_UNKNOWN, (result as RouteLookupResult.Failed).failure)
    }
}
