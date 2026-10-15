package io.opentrack.app.source

import io.opentrack.core.contract.SourceFailure
import io.opentrack.core.diag.Diagnostics
import io.opentrack.core.railyatri.RailYatriParser
import io.opentrack.core.railyatri.RailYatriRunningStatus
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class RailYatriRunningStatusSource(
    private val openConnection: (URL) -> HttpURLConnection,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val enabled: Boolean = true,
    private val diagnostics: Diagnostics? = null,
    private val endpoint: String? = null,
    private val cacheTtlMs: Long = 60_000L,
) {
    private val cache = TtlCache<String, RailYatriRunningStatus> (clock)

    sealed interface Result {
        data class Live(val status: RailYatriRunningStatus) : Result
        data class Failed(val failure: SourceFailure, val detail: String) : Result
    }

    fun fetch(trainNumber: String): Result {
        if (!enabled) return Result.Failed(SourceFailure.OFFLINE, "source disabled")
        val train = trainNumber.trim()
        cache.get(train, cacheTtlMs)?.let { return Result.Live(it.value.copy(viaCache = true)) }
        val candidates = mutableListOf<String>()
        if (endpoint != null) candidates.add(endpoint!!)
        candidates.add("https://www.railyatri.in/m/live-train-status?trainNo=$train")
        candidates.add("https://www.railyatri.in/live-train-status/$train")
        candidates.add("https://www.railyatri.in/api/mobile/train/live-status?trainNo=$train")

        val headerSets = listOf(
            mapOf("User-Agent" to "com.railyatri.in/11.2.3", "Accept" to "application/json", "X-Requested-With" to "XMLHttpRequest", "Referer" to "https://www.railyatri.in/m/live-train-status"),
            mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36", "Accept" to "application/json, */*", "X-Requested-With" to "XMLHttpRequest", "Referer" to "https://www.railyatri.in/live-train-status/$train"),
            mapOf("User-Agent" to "Dalvik/2.1.0 (Linux; Android 11)", "Accept" to "application/json"),
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
                        val parsed = RailYatriParser.parse(body, clock(), viaCache = false)
                        if (parsed != null) {
                            cache.put(train, parsed)
                            return Result.Live(parsed)
                        }
                    }
                } catch (e: IOException) {
                    // continue
                }
            }
        }
        return Result.Failed(SourceFailure.SHAPE_UNKNOWN, "all candidates failed or shape unknown; needs device-captured endpoint")
    }
}
