package io.opentrack.app.source

import io.opentrack.core.contract.SourceFailure
import io.opentrack.core.ntes.NtesCrypto
import io.opentrack.core.ntes.NtesLiveStatus
import io.opentrack.core.ntes.NtesLiveStatusParser
import io.opentrack.core.parse.ErailTrainParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface LiveStatusLookup {
    data class Live(val status: NtesLiveStatus, val rawJson: String) : LiveStatusLookup
    data class Failed(val failure: SourceFailure, val detail: String) : LiveStatusLookup
}

/**
 * S1 NTES live running status (per-stop ACTUALS' source). Keyless: no key is issued to the user,
 * no account, no relay - the request carries only the official app's public envelope constants
 * (3.1). Transport is injected so this is testable offline (6.3); the response's per-stop array is
 * deliberately NOT parsed (no capture yet, 7.4) - the raw decoded body is returned for the
 * diagnostics collector instead, and the screen states what is and is not shown.
 */
class NtesRunningStatusSource(
    private val openConnection: (URL) -> HttpURLConnection,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val enabled: Boolean = true,
    private val dateProvider: () -> String = { defaultDate(clock()) },
) {
    fun fetch(trainNumber: String): LiveStatusLookup {
        if (!enabled) {
            return LiveStatusLookup.Failed(SourceFailure.OFFLINE, "source disabled by flag (12.6)")
        }
        val payload = "service=TrainRunningMob&subService=ShowFullRunJson" +
            "&trainNo=${trainNumber.trim()}&startDate=${dateProvider()}"
        val reply = try {
            post(payload)
        } catch (e: IOException) {
            return LiveStatusLookup.Failed(
                if (e is java.net.SocketTimeoutException) SourceFailure.TIMEOUT else SourceFailure.OFFLINE,
                e.message ?: e.javaClass.simpleName,
            )
        }
        val status = reply.status
        if (status !in 200..299) {
            return LiveStatusLookup.Failed(SourceFailure.HTTP, "HTTP $status")
        }
        val body = reply.body
        if (body.isBlank()) {
            return LiveStatusLookup.Failed(SourceFailure.EMPTY, "2xx with an empty body")
        }
        val jsonIn = try {
            Regex("\"jsonIn\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
                ?: return LiveStatusLookup.Failed(SourceFailure.SHAPE_UNKNOWN, "no jsonIn field")
        } catch (e: Exception) {
            return LiveStatusLookup.Failed(SourceFailure.SHAPE_UNKNOWN, "unreadable envelope")
        }
        val decoded = NtesCrypto.decodeResponse(jsonIn)
            ?: return LiveStatusLookup.Failed(SourceFailure.SHAPE_UNKNOWN, "envelope did not decode")
        val status2 = NtesLiveStatusParser.parse(decoded)
            ?: return LiveStatusLookup.Failed(SourceFailure.SHAPE_UNKNOWN,
                "the response is not the documented status shape (protocol may have changed, 12.3)")
        return LiveStatusLookup.Live(status2, decoded)
    }

    private data class Reply(val status: Int, val body: String)

    private fun post(payload: String): Reply {
        val connection = openConnection(URL(NtesCrypto.BASE_URL))
        connection.requestMethod = "POST"
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("charset", "utf-8")
        connection.setRequestProperty("User-Agent", NtesCrypto.USER_AGENT)
        // the server only accepts legacy static-RSA TLS 1.2 suites (documented public client
        // notes the same handshake failure); widening the client's offer is harmless.
        if (connection is javax.net.ssl.HttpsURLConnection) {
            connection.sslSocketFactory = legacyTlsFactory()
        }
        connection.doOutput = true
        connection.outputStream.use { it.write(bodyFor(payload).toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val body = try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        connection.disconnect()
        return Reply(status, body)
    }

    private fun bodyFor(payload: String): String =
        org.json.JSONObject().put("jsonIn", NtesCrypto.buildRequest(payload)).toString()

    private fun legacyTlsFactory(): javax.net.ssl.SSLSocketFactory {
        val context = javax.net.ssl.SSLContext.getInstance("TLSv1.2")
        context.init(null, null, null)
        return context.socketFactory
    }

        companion object {
            /** NTES dates are DD-MMM-YYYY in IST (10.3: the enquiry day, not the device's). */
            fun defaultDate(millis: Long): String {
                val ist = millis + 5 * 60 * 60 * 1000L + 30 * 60 * 1000L
                val days = Math.floorDiv(ist, 86_400_000L)
                val ymd = ErailTrainParser.civilFromDays(days) // the core's tested inverse (7.3)
                val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                return "%02d-%s-%04d".format(ymd[2], months[ymd[1] - 1], ymd[0])
            }

        internal fun civilFromDays(days: Long): IntArray {
            var z = days + 719_468L
            val era = (if (z >= 0) z else z - 146_096) / 146_097
            val doe = z - era * 146_097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val y = yoe + era * 400
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
            return intArrayOf((if (month <= 2) y + 1 else y).toInt(), month, day)
        }
    }
}
