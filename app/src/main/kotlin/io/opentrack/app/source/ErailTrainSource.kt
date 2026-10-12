package io.opentrack.app.source

import io.opentrack.core.contract.SourceFailure
import io.opentrack.core.diag.DiagnosticsCodec
import io.opentrack.core.parse.ErailTrainParser
import io.opentrack.core.parse.ErailTrainPayload
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Transport reply as measured on the wire; the parser never sees the socket (6.3). */
data class TransportReply(val status: Int, val body: String?)

fun interface ErailTransport {
    /** May throw IOException; the source maps that to a typed failure (9.3: no silent catch). */
    fun get(path: String): TransportReply
}

/** Real transport: bounded, cancellable, honours the endpoint as a production system (3.5/3.8). */
class HttpErailTransport(
    private val baseUrl: String = "https://erail.in",
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 12_000,
) : ErailTransport {
    override fun get(path: String): TransportReply {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val status = connection.responseCode
            val body = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            return TransportReply(status, body)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val USER_AGENT = "OpenTrackIndia/2.1 (Android; keyless enquiry)"
    }
}

sealed interface TrainLookup {
    data class Live(
        val payload: ErailTrainPayload,
        val wire: Map<String, String> = emptyMap(),
    ) : TrainLookup
    data class Failed(
        val failure: SourceFailure,
        val detail: String,
        val wire: Map<String, String> = emptyMap(),
    ) : TrainLookup
}

/**
 * S2 train-number source. Transport is INJECTED so fetch+parse are unit-testable offline (6.3);
 * the shape guard refuses throttle-shaped bodies before parsing (7.4, LEARNINGS 1).
 */
class ErailTrainSource(
    private val transport: ErailTransport,
    private val enabled: Boolean = true,
) {
    fun fetch(number: String): TrainLookup {
        if (!enabled) return TrainLookup.Failed(SourceFailure.OFFLINE, "source disabled by flag (12.6)")
        val reply = try {
            transport.get("/rail/getTrains.aspx?TrainNo=$number")
        } catch (e: IOException) { // named: network failure is OFFLINE/TIMEOUT, never an empty answer
            return TrainLookup.Failed(
                if (isTimeout(e)) SourceFailure.TIMEOUT else SourceFailure.OFFLINE,
                e.message ?: e.javaClass.simpleName,
            )
        }
        if (reply.status !in 200..299) {
            return TrainLookup.Failed(
                SourceFailure.HTTP, "HTTP ${reply.status}",
                wire = DiagnosticsCodec.signature(reply.body.orEmpty()) + mapOf("http_status" to reply.status.toString()),
            )
        }
        val body = reply.body
        if (body.isNullOrBlank()) {
            return TrainLookup.Failed(SourceFailure.EMPTY, "2xx with an empty body")
        }
        val payload = ErailTrainParser.parse(body)
            ?: return TrainLookup.Failed(
                SourceFailure.SHAPE_UNKNOWN,
                "the feed did not match its captured shape; it may have changed (12.3)",
                wire = DiagnosticsCodec.signature(body),
            )
        return TrainLookup.Live(payload, DiagnosticsCodec.signature(body))
    }

    private fun isTimeout(e: IOException): Boolean =
        e is java.net.SocketTimeoutException || e.message?.contains("timed out", true) == true
}
