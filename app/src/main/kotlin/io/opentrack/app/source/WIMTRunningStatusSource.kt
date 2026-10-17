package io.opentrack.app.source

import io.opentrack.core.contract.SourceFailure
import io.opentrack.core.diag.Diagnostics
import io.opentrack.core.ntes.TtlCache
import io.opentrack.core.wimt.WIMTParser
import io.opentrack.core.wimt.WIMTRunningStatus
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class WIMTRunningStatusSource(
    private val openConnection: (URL) -> HttpURLConnection,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val enabled: Boolean = true,
    private val diagnostics: Diagnostics? = null,
    private val endpoint: String? = null,
    private val cacheTtlMs: Long = 60_000L,
) {
    private val cache = TtlCache<String, WIMTRunningStatus>(clock)

    sealed interface Result {
        data class Live(val status: WIMTRunningStatus) : Result
        data class Failed(val failure: SourceFailure, val detail: String) : Result
    }

    private val dateFmt = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    fun fetch(trainNumber: String, from: String?, to: String?, trainDate: String?): Result {
        if (!enabled) return Result.Failed(SourceFailure.OFFLINE, "source disabled")
        val train = trainNumber.trim()
        val key = "$train|${from ?: ""}|${to ?: ""}|${trainDate ?: ""}"
        cache.get(key, cacheTtlMs)?.let { return Result.Live(it.value.copy(viaCache = true)) }
        val candidates = mutableListOf<String>()
        if (endpoint != null) candidates.add(endpoint!!)
        // whereismytrain.in cache/live_status returns real JSON per-stop data with actuals and delays
        val date = trainDate ?: dateFmt.format(Date())
        val fromParam = from ?: "MYS"
        val toParam = to ?: "TLGP"
        candidates.add(
            "https://whereismytrain.in/cache/live_status?date=$date&train_no=$train&from=$fromParam&to=$toParam&from_day=1&lang=en&appVersion=7.1.5.633808517&sb_version=0"
        )

        val headerSets = listOf(
            mapOf("Accept" to "application/json",
                  "User-Agent" to "OpenTrack India source-research/1.0"),
            mapOf("Accept" to "application/json",
                  "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120 Safari/537.36"),
        )

        for (urlStr in candidates) {
            for (headers in headerSets) {
                try {
                    val conn = openConnection(URL(urlStr))
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 8000
                    conn.readTimeout = 10000
                    headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                    val status = conn.responseCode
                    val body = try {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } catch (e: IOException) {
                        conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                    if (status in 200..299 && body.isNotBlank()) {
                        val parsed = WIMTParser.parse(body, clock(), viaCache = false)
                        if (parsed != null) {
                            cache.put(key, parsed)
                            return Result.Live(parsed)
                        }
                    }
                } catch (e: IOException) {
                    // continue
                }
            }
        }
        return Result.Failed(SourceFailure.SHAPE_UNKNOWN, "all candidates failed or shape unknown")
    }
}
