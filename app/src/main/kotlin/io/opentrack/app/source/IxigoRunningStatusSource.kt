package io.opentrack.app.source

import io.opentrack.core.contract.SourceFailure
import io.opentrack.core.diag.Diagnostics
import io.opentrack.core.ixigo.IxigoParser
import io.opentrack.core.ixigo.IxigoRunningStatus
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class IxigoRunningStatusSource(
    private val openConnection: (URL) -> HttpURLConnection,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val enabled: Boolean = true,
    private val diagnostics: Diagnostics? = null,
    private val endpoint: String? = null,
) {
    sealed interface Result {
        data class Live(val status: IxigoRunningStatus) : Result
        data class Failed(val failure: SourceFailure, val detail: String) : Result
    }

    fun fetch(trainNumber: String): Result {
        if (!enabled) return Result.Failed(SourceFailure.OFFLINE, "source disabled")
        val train = trainNumber.trim()
        val candidates = mutableListOf<String>()
        if (endpoint != null) candidates.add(endpoint!!)
        candidates.add("https://www.ixigo.com/api/v3/trains/running-status?trainNo=$train")
        candidates.add("https://www.ixigo.com/api/trains/running-status?trainNo=$train")
        candidates.add("https://www.ixigo.com/trains/train-running-status?trainNo=$train")

        val headerSets = listOf(
            mapOf("User-Agent" to "com.ixigo.train/9.0.8", "Accept" to "application/json", "X-Requested-With" to "XMLHttpRequest", "Referer" to "https://www.ixigo.com/trains/train-running-status"),
            mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36", "Accept" to "application/json, */*", "X-Requested-With" to "XMLHttpRequest"),
        )

        for (urlStr in candidates) {
            for (headers in headerSets) {
                try {
                    val conn = openConnection(URL(urlStr))
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 8000
                    conn.readTimeout = 10000
                    headers.forEach { (k,v) -> conn.setRequestProperty(k,v) }
                    val status = conn.responseCode
                    val body = try {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } catch (e: IOException) {
                        conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                    if (status in 200..299 && body.isNotBlank()) {
                        val parsed = IxigoParser.parse(body, clock(), viaCache = false)
                        if (parsed != null) return Result.Live(parsed)
                    }
                } catch (e: IOException) {
                }
            }
        }
        return Result.Failed(SourceFailure.SHAPE_UNKNOWN, "all candidates failed or shape unknown; needs device-captured endpoint")
    }
}
