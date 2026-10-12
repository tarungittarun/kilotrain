package io.opentrack.app.source

import io.opentrack.core.contract.SourceFailure
import io.opentrack.core.parse.ErailRouteParser
import io.opentrack.core.diag.DiagnosticsCodec
import io.opentrack.core.parse.RouteStopSchedule
import java.io.IOException

sealed interface RouteLookupResult {
    data class Live(
        val stops: List<RouteStopSchedule>,
        val wire: Map<String, String> = emptyMap(),
    ) : RouteLookupResult
    data class Failed(
        val failure: SourceFailure,
        val detail: String,
        val wire: Map<String, String> = emptyMap(),
    ) : RouteLookupResult
}

/**
 * Per-stop schedule source. Two measured requests per user action (3.5): the train-number payload
 * (which carries the train id) then `data.aspx?Action=TRAINROUTE&Password=2012&Data1=<id>&Data2=EN`
 * whose parameter names and constants were read from erail's own site code. Transport injected, so
 * this is unit-testable offline (6.3); a non-family body is a stated failure, never a guess (7.4).
 */
class ErailRouteSource(
    private val transport: ErailTransport,
    private val numberSource: ErailTrainSource,
    private val enabled: Boolean = true,
) {
    fun fetch(trainNumber: String): RouteLookupResult {
        if (!enabled) {
            return RouteLookupResult.Failed(SourceFailure.OFFLINE, "source disabled by flag (12.6)")
        }
        val payload = when (val found = numberSource.fetch(trainNumber)) {
            is TrainLookup.Live -> found.payload
            is TrainLookup.Failed -> return RouteLookupResult.Failed(found.failure, found.detail, found.wire)
        }
        val trainId = payload.trainId
            ?: return RouteLookupResult.Failed(
                SourceFailure.SHAPE_UNKNOWN, "the train's id was not in its payload"
            )
        val reply = try {
            transport.get("/data.aspx?Action=TRAINROUTE&Password=2012&Data1=$trainId&Data2=EN&C=false")
        } catch (e: IOException) {
            return RouteLookupResult.Failed(
                if (e is java.net.SocketTimeoutException) SourceFailure.TIMEOUT else SourceFailure.OFFLINE,
                e.message ?: e.javaClass.simpleName,
            )
        }
        if (reply.status !in 200..299) {
            return RouteLookupResult.Failed(
                SourceFailure.HTTP, "HTTP ${reply.status}",
                wire = DiagnosticsCodec.signature(reply.body.orEmpty()) + mapOf("http_status" to reply.status.toString()),
            )
        }
        val body = reply.body.orEmpty()
        val stops = ErailRouteParser.parse(body)
            ?: return RouteLookupResult.Failed(
                SourceFailure.SHAPE_UNKNOWN,
                "the route did not match its captured shape; it may have changed (12.3)",
                wire = DiagnosticsCodec.signature(body),
            )
        return RouteLookupResult.Live(stops, DiagnosticsCodec.signature(body))
    }
}
