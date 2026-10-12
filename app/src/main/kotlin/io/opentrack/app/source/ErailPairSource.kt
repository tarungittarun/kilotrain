package io.opentrack.app.source

import io.opentrack.core.contract.SourceFailure
import io.opentrack.core.parse.ErailPairParser
import io.opentrack.core.parse.ErailPairResponse
import java.io.IOException

/** Pair lookup result: real trains from a captured-family payload, or a typed failure. */
sealed interface PairLookupResult {
    data class Live(val response: ErailPairResponse) : PairLookupResult
    data class Failed(val failure: SourceFailure, val detail: String) : PairLookupResult
}

/**
 * S2 station-pair source (ADR 0010). The query runs at RUNTIME from the passenger's device, so it
 * uses the passenger's network - the same one that reaches these sources. Transport is injected so
 * the path is unit-testable offline (6.3), and the shape guard refuses anything outside the
 * captured family (7.4/12.3): a throttle message or changed protocol becomes a stated failure,
 * never a guess.
 */
class ErailPairSource(
    private val transport: ErailTransport,
    private val enabled: Boolean = true,
) {
    fun fetch(fromStation: String, toStation: String): PairLookupResult {
        if (!enabled) {
            return PairLookupResult.Failed(SourceFailure.OFFLINE, "source disabled by flag (12.6)")
        }
        val reply = try {
            // a handful of requests per user action only (3.5): exactly one per search
            transport.get("/rail/getTrains.aspx?l=$fromStation&c=$toStation")
        } catch (e: IOException) {
            return PairLookupResult.Failed(
                if (e is java.net.SocketTimeoutException) SourceFailure.TIMEOUT else SourceFailure.OFFLINE,
                e.message ?: e.javaClass.simpleName,
            )
        }
        if (reply.status !in 200..299) {
            return PairLookupResult.Failed(SourceFailure.HTTP, "HTTP ${reply.status}")
        }
        val body = reply.body
        if (body.isNullOrBlank()) {
            return PairLookupResult.Failed(SourceFailure.EMPTY, "2xx with an empty body")
        }
        val response = ErailPairParser.parse(body)
            ?: return PairLookupResult.Failed(
                SourceFailure.SHAPE_UNKNOWN,
                "the feed did not match its captured shape; it may have changed (12.3). "
                    + "signature=${ErailPairParser.signatureOf(body)}",
            )
        return PairLookupResult.Live(response)
    }
}
