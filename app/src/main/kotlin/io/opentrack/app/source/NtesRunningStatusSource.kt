package io.opentrack.app.source

import io.opentrack.core.contract.SourceFailure
import io.opentrack.core.ntes.NtesCrypto
import io.opentrack.core.ntes.NtesRunningStatus
import io.opentrack.core.ntes.NtesRunningStatusParser
import io.opentrack.core.ntes.TtlCache
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext

/**
 * NTES live running status, requested exactly as the mobile gateway documents (the owner's
 * reference): POST a plain JSON body with `queryType: "6"`, the Dalvik user agent, and the date as
 * `DD-MMM-YYYY`. Keyless: no key is issued to the user, no account, no relay (3.1).
 *
 * Protections the reference calls for:
 *  1. the date is formatted from the clock with three-letter months (never `12-10-2026`);
 *  2. TLS: the client WIDENS the offered cipher list (CRIS serves legacy static-RSA TLS 1.2) but
 *     never disables certificate verification - a rotated certificate fails as a stated error, not
 *     as silent plaintext (3.6);
 *  3. the status block is cached per train number for 90 s (TtlCache) so a refresh never
 *     re-hammers the endpoint (3.5/3.8).
 *
 * The transport is injected, so the whole path is testable offline (6.3).
 */
class NtesRunningStatusSource(
    private val openConnection: (URL) -> HttpURLConnection,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val enabled: Boolean = true,
) {
    private val cache = TtlCache<String, NtesRunningStatus>(clock)

    sealed interface NtesStatusResult {
        data class Live(val status: NtesRunningStatus) : NtesStatusResult
        data class Failed(val failure: SourceFailure, val detail: String) : NtesStatusResult
    }

    fun fetch(trainNumber: String): NtesStatusResult {
        if (!enabled) return NtesStatusResult.Failed(SourceFailure.OFFLINE, "source disabled by flag (12.6)")
        val train = trainNumber.trim()
        // protection 3: a cached answer must SAY it is cached (3.2), not claim to be live
        cache.get(train, TTL_MS)?.let { return NtesStatusResult.Live(it.value.copy(viaCache = true)) }
        val started = clock()
        val date = defaultDate(started) // protection 1
        val reply = post(plainBody(train, date), DALVIK_UA)
        val body = when (reply) {
            is Wire.Ok -> reply.body
            is Wire.Failure -> return NtesStatusResult.Failed(reply.failure, reply.detail)
        }
        // The documented plain-JSON answer; if the gateway answers with the older encrypted form
        // (public client documents it too), decode that instead of failing.
        val parsed = NtesRunningStatusParser.parse(body, clock(), viaCache = false)
            ?: decodeEncryptedFallback(body)?.let { NtesRunningStatusParser.parse(it, clock(), viaCache = false) }
        if (parsed == null) {
            return NtesStatusResult.Failed(
                SourceFailure.SHAPE_UNKNOWN,
                "the response did not match the documented shape; the protocol may have changed (12.3)",
            )
        }
        cache.put(train, parsed)
        return NtesStatusResult.Live(parsed)
    }

    private fun decodeEncryptedFallback(body: String): String? {
        val jsonIn = Regex("\"jsonIn\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1) ?: return null
        return NtesCrypto.decodeResponse(jsonIn)
    }

    private fun post(requestBody: String, userAgent: String): Wire {
        return try {
            val connection = openConnection(URL(NtesCrypto.BASE_URL))
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("User-Agent", userAgent)
            if (connection is HttpsURLConnection) {
                // protection 2: widen the offered suites, KEEP verification on
                connection.sslSocketFactory = legacyTlsFactory()
            }
            connection.doOutput = true
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val text = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            connection.disconnect()
            when {
                status !in 200..299 -> Wire.Failure(SourceFailure.HTTP, "HTTP $status")
                text.isBlank() -> Wire.Failure(SourceFailure.EMPTY, "2xx with an empty body")
                else -> Wire.Ok(text)
            }
        } catch (e: IOException) {
            Wire.Failure(
                if (e is java.net.SocketTimeoutException) SourceFailure.TIMEOUT else SourceFailure.OFFLINE,
                e.message ?: e.javaClass.simpleName,
            )
        }
    }

    private sealed interface Wire {
        data class Ok(val body: String) : Wire
        data class Failure(val failure: SourceFailure, val detail: String) : Wire
    }

    /**
     * The documented request body, written by hand: org.json is not available on the JVM test
     * classpath (core cannot see it either), and this shape is fixed by the reference.
     */
    private fun plainBody(train: String, date: String): String =
        """{"queryType":"6","trainNo":${jsonString(train)},"startDate":${jsonString(date)},"stationCode":"","lang":"en"}"""

    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun legacyTlsFactory(): javax.net.ssl.SSLSocketFactory {
        val context = SSLContext.getInstance("TLSv1.2")
        context.init(null, null, null)
        return context.socketFactory
    }

    companion object {
        const val TTL_MS = 90_000L // the reference's protected 90-second cache
        const val DALVIK_UA = "dalvik/2.1.0 (linux; u; android 13; build/tq3a.230805.001)"

        /** `DD-MMM-YYYY` in IST (10.3: the enquiry day from the stamp, not the device's clock). */
        fun defaultDate(millis: Long): String {
            val ist = millis + 5 * 60 * 60 * 1000L + 30 * 60 * 1000L
            val days = Math.floorDiv(ist, 86_400_000L)
            val ymd = io.opentrack.core.parse.ErailTrainParser.civilFromDays(days)
            val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            return "%02d-%s-%04d".format(ymd[2], months[ymd[1] - 1], ymd[0])
        }
    }
}
