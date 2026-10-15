package io.opentrack.app.source

import io.opentrack.core.confirmtkt.ConfirmTktParser
import io.opentrack.core.confirmtkt.ConfirmTktRunningStatus
import io.opentrack.core.contract.SourceFailure
import io.opentrack.core.diag.Diagnostics
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ConfirmTktRunningStatusSource(
    private val openConnection: (URL) -> HttpURLConnection,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val enabled: Boolean = true,
    private val diagnostics: Diagnostics? = null,
    private val endpoint: String? = null,
) {
    sealed interface Result {
        data class Live(val status: ConfirmTktRunningStatus) : Result
        data class Failed(val failure: SourceFailure, val detail: String) : Result
    }

    fun fetch(trainNumber: String): Result {
        if (!enabled) return Result.Failed(SourceFailure.OFFLINE, "source disabled (flag)")
        val train = trainNumber.trim()
        val url = endpoint ?: "https://www.confirmtkt.com/api/v2/train-running-status?trainNo=$train"
        try {
            val conn = openConnection(URL(url))
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", "Dalvik/2.1.0 (Linux; Android 11)")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            conn.setRequestProperty("Referer", "https://www.confirmtkt.com/train-running-status")
            conn.setRequestProperty("Origin", "https://www.confirmtkt.com")
            val status = conn.responseCode
            val body = try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            if (status !in 200..299 || body.isBlank()) {
                return Result.Failed(SourceFailure.HTTP, "HTTP $status")
            }
            val parsed = ConfirmTktParser.parse(body, clock(), viaCache = false)
            if (parsed == null) {
                diagnostics?.sourceFailure(mapOf("op" to "confirmtkt_status", "failure" to "SHAPE_UNKNOWN"))
                return Result.Failed(SourceFailure.SHAPE_UNKNOWN, "shape unknown; needs captured JSON")
            }
            return Result.Live(parsed)
        } catch (e: IOException) {
            val f = if (e is java.net.SocketTimeoutException) SourceFailure.TIMEOUT else SourceFailure.OFFLINE
            return Result.Failed(f, e.message ?: "io")
        }
    }
}
